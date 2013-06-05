package org.kevoree.library.sky.jails

import nodeType.JailNode
import org.kevoree.api.service.core.script.KevScriptEngine
import org.kevoree.library.sky.jails.process.ProcessExecutor
import org.slf4j.{LoggerFactory, Logger}
import org.kevoree.framework.Constants

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 07/11/12
 * Time: 23:45
 *
 * @author Erwan Daubert
 * @version 1.0
 */
object JailsReasoner {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val processExecutor = new ProcessExecutor()

  def createNodes(kengine: KevScriptEngine, iaasNode: JailNode): Boolean = {
    logger.debug("Trying to identify the set of already existing jails to add them on the model")
    // use ezjail-admin list and parse the result to get the IP and the name of the jail and the id
    val result = processExecutor.listJails()
    if (result._1) {
      result._2.foreach {
        jail =>
          createNode(kengine, iaasNode, jail._1, jail._2, jail._3)
      }
      true
    } else {
      logger.debug("Unable to get jails configuration. Existing jails will not be added on the current configuration")
      false
    }
  }

  def createNode(kengine: KevScriptEngine, iaasNode: JailNode, jid: String, ips: String, name: String) {
    // create a node for each existing jail
    logger.debug("Trying to add the jail {} with {} as IPs", Array[String](name, ips))
    kengine addVariable("nodeName", name)
    kengine addVariable("parentName", iaasNode.getName)
    kengine append "addNode {nodeName} : PJailNode\n"
    kengine append "addChild {nodeName}@{parentName}\n"

    kengine.addVariable("ipKey", Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP)
    ips.split(",").foreach {
      ip =>
        kengine addVariable("ip", ip)
        kengine append "network {nodeName} {'{ipKey}' = '{ip}' }\n"
    }

    // TODO list the available constraints for each jail's id to get CPU, RAM
    val constraints = JailsConstraintsConfiguration.getJailConstraints(name)
    if (constraints._1) {
      constraints._2.foreach {
        constraint =>
          println(constraint._1 + "=" + constraint._2)
          /*kengine addVariable("key", constraint._1)
          kengine addVariable("value", constraint._2)
          kengine append ("updateDictionary {nodeName} { {key} = '{value}' }\n")*/
      }
    } else {
      logger.debug("Unable to get constraint about node {}", name)
    }
  }

}
