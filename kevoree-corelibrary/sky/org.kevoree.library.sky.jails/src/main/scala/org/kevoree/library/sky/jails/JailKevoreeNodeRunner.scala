package org.kevoree.library.sky.jails

import nodeType.JailNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import process.ProcessExecutor
import org.kevoree.library.sky.api.KevoreeNodeRunner
import java.io._
import org.kevoree.{ContainerNode, ContainerRoot}
import org.kevoree.framework.{KevoreeXmiHelper, Constants, KevoreePropertyHelper}
import scala.collection.JavaConversions._


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 20/09/11
 * Time: 11:46
 *
 * @author Erwan Daubert
 * @version 1.0
 */
class JailKevoreeNodeRunner(nodeName: String, iaasNode: JailNode, addTimeout : Long, removeTimeout : Long, managedChildKevoreePlatform : Boolean) extends KevoreeNodeRunner(nodeName) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val processExecutor = new ProcessExecutor()

  //  var nodeProcess: Process = null

  def startNode(iaasModel: ContainerRoot, childBootstrapModel: ContainerRoot): Boolean = {
    val beginTimestamp = System.currentTimeMillis()
    logger.debug("Starting " + nodeName)
    iaasModel.findByPath("nodes[" + iaasNode.getName + "]/hosts[" + nodeName + "]", classOf[ContainerNode]) match {
      case node: ContainerNode => {
        // looking for currently launched jail
        val result = processExecutor.listIpJails(nodeName)
        if (result._1) {
          var newIps = List("127.0.0.1")
          // check if the node have a inet address
          val ips = KevoreePropertyHelper.$instance.getNetworkProperties(iaasModel, nodeName, Constants.$instance.getKEVOREE_PLATFORM_REMOTE_NODE_IP)
          if (ips.size() > 0) {
            newIps = ips.toList
          } else {
            logger.warn("Unable to get the IP for the new jail, the creation may fail")
          }
          if (processExecutor.addNetworkAlias(iaasNode.getNetworkInterface, newIps, iaasNode.getAliasMask)) {
            // looking for the flavors
            var flavor = lookingForFlavors(iaasModel, node)
            if (flavor == null) {
              flavor = iaasNode.getDefaultFlavor
            }
            // create the new jail
            if (processExecutor.createJail(flavor, nodeName, newIps, findArchive(nodeName), addTimeout - (System.currentTimeMillis() - beginTimestamp))) {
              var jailPath = processExecutor.findPathForJail(nodeName)
              // find the needed version of Kevoree for the child node
              val version = findVersionForChildNode(nodeName, childBootstrapModel, iaasModel.getNodes.find(n => n.getName == iaasNode.getNodeName).get)
              // install the model on the jail
              val platformFile = iaasNode.getBootStrapperService.resolveKevoreeArtifact("org.kevoree.platform.standalone", "org.kevoree.platform", version)
              KevoreeXmiHelper.$instance.save(jailPath + File.separator + "root" + File.separator + "bootstrapmodel.kev", childBootstrapModel)
              if (copyFile(platformFile.getAbsolutePath, jailPath + File.separator + "root" + File.separator + "kevoree-runtime.jar")) {
                // specify limitation on jail such as CPU, RAM
                if (JailsConstraintsConfiguration.applyJailConstraints(iaasModel, node)) {
                  // configure ssh access
                  configureSSHServer(jailPath, newIps)
                  // launch the jail
                  if (processExecutor.startJail(nodeName, addTimeout - (System.currentTimeMillis() - beginTimestamp))) {
                    logger.debug("{} started", nodeName)
                    val jailData = processExecutor.findJail(nodeName)
                    jailPath = jailData._1
                    val jailId = jailData._2
                    if (jailId != "-1") {
                      logger.debug("Jail {} is correctly configure, now we try to start the Kevoree Node", nodeName)
                      val logFile = System.getProperty("java.io.tmpdir") + File.separator + nodeName + ".log"
                      outFile = new File(logFile + ".out")
                      errFile = new File(logFile + ".err")
                      var property = KevoreePropertyHelper.$instance.getProperty(node, "RAM", false, "")
                      if (property == null) {
                        property = "N/A"
                      }
                      processExecutor.startKevoreeOnJail(jailId, property, nodeName /*, outFile, errFile*/ , this, iaasNode, managedChildKevoreePlatform)
                    } else {
                      logger.error("Unable to find the jail {}", nodeName)
                      false
                    }
                  } else {
                    logger.error("Unable to start the jail {}", nodeName)
                    false
                  }
                } else {
                  logger.error("Unable to specify jail limitations on {}", nodeName)
                  false
                }
              } else {
                logger.error("Unable to set the model the new jail {}", nodeName)
                false
              }
            } else {
              logger.error("Unable to create a new Jail {}", nodeName)
              false
            }
          } else {
            logger.error("Unable to define a new alias {} with {}", Array[String](nodeName, newIps.mkString(", ")))
            false
          }
        } else {
          // if an existing one have the same name, then it is not possible to launch this new one (return false)
          logger.error("There already exists a jail with the same name or it is not possible to check this:\n {}", result._2)
          false
        }
      }
      case null => logger.error("the model that must be applied doesn't contain the node {}", nodeName); false
    }


  }

  def stopNode(): Boolean = {
    val beginTimestamp = System.currentTimeMillis()
    logger.info("stop " + nodeName)
    // looking for the jail that must be at least created
    val oldIP = processExecutor.findJail(nodeName)._3
    if (oldIP != "-1") {
      // stop the jail
      if (processExecutor.stopJail(nodeName, removeTimeout - (System.currentTimeMillis() - beginTimestamp))) {
        // delete the jail
        if (processExecutor.deleteJail(nodeName, removeTimeout - (System.currentTimeMillis() - beginTimestamp))) {
          // release IP alias to allow next IP select to use this one
          if (!processExecutor.deleteNetworkAlias(iaasNode.getNetworkInterface, oldIP)) {
            logger.warn("unable to release ip alias {} for the network interface {}", Array[String](oldIP, iaasNode.getNetworkInterface))
          }
          // remove rctl constraint using rctl -r jail:<jailNode>
          if (!JailsConstraintsConfiguration.removeJailConstraints(nodeName)) {
            logger.warn("unable to remove rctl constraints about {}", nodeName)
          }
          true
        } else {
          logger.error("Unable to delete the jail {}", nodeName)
          false
        }
      } else {
        logger.error("Unable to stop the jail {}", nodeName)
        false
      }
    } else {
      // if there is no jail corresponding to the nodeName then it is not possible to stop and delete it
      logger.error("Unable to find the corresponding jail {}", nodeName)
      false
    }
  }

  private def lookingForFlavors(iaasModel: ContainerRoot, node: ContainerNode): String = {
    logger.debug("looking for specific flavor")
    val flavorsOption = KevoreePropertyHelper.$instance.getProperty(node, "flavor", false, "")
    if (flavorsOption != null && iaasNode.getAvailableFlavors.contains(flavorsOption)) {
      flavorsOption
    } else if (flavorsOption != null && !iaasNode.getAvailableFlavors.contains(flavorsOption)) {
      logger.warn("Unknown flavor ({}) or unavailable flavor on {}", Array[String] (flavorsOption, iaasNode.getName))
      null
    } else {
      null
    }
  }

  private def findArchive(nodName: String): Option[String] = {
    // TODO
    //val archivesURL = iaasNode.getArchives


    None
  }
}

