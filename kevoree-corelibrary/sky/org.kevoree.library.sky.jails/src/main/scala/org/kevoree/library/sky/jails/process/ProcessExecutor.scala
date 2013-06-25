package org.kevoree.library.sky.jails.process

import scala.Array._
import util.matching.Regex
import java.io.File
import org.kevoree.library.sky.api.KevoreeNodeRunner
import org.slf4j.{LoggerFactory, Logger}
import org.kevoree.library.sky.api.property.PropertyConversionHelper
import org.kevoree.library.sky.api.nodeType.helper.SubnetUtils
import scala.Array
import org.kevoree.library.sky.jails.JailsConstraintsConfiguration
import org.kevoree.{ContainerRoot, ContainerNode}
import org.kevoree.framework.AbstractNodeType


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 22/03/12
 * Time: 09:32
 *
 * @author Erwan Daubert
 * @version 1.0
 **/

class ProcessExecutor() {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val listJailsProcessBuilder = new ProcessBuilder
  listJailsProcessBuilder.command("/usr/local/bin/ezjail-admin", "list")

  val jlsPattern = ".* ip4.addr=((?:(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?),?)*) .* ip6.addr=(?:(?:(?:([0-9a-zA-Z]{0,4}:?){8}),?)*) .*"
  val jlsRegex = new Regex(jlsPattern)

  val ezjailListPattern = "(D.?)\\ \\ *([0-9][0-9]*|N/A)\\ \\ *((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))\\ \\ *([a-zA-Z0-9\\.][a-zA-Z0-9_\\.]*)\\ \\ *((?:(?:/[a-zA-Z0-9_\\.][a-zA-Z0-9_\\.]*)*))"
  val ezjailListRegex = new Regex(ezjailListPattern)

  val ifconfig = "/sbin/ifconfig"
  val ezjailAdmin = "/usr/local/bin/ezjail-admin"
  val jexec = "/usr/sbin/jexec"

  var currentProcess: Process = null

  def listJails(): (Boolean, List[(String, String, String)]) = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(2000)
    var jailConfigurations = List[(String, String, String)]()
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              val resultActor = new ResultManagementActor()
              resultActor.starting()
              currentProcess = Runtime.getRuntime.exec(Array[String]("jls", "-n", "-j", jid))
              new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(jlsRegex), Array(), currentProcess)).start()
              val result2 = resultActor.waitingFor(2000)
              var ips = ""
              if (result2._1) {
                result2._2.split("\n").foreach {
                  line2 =>
                    line2 match {
                      case jlsRegex(ipsv4, ipsv6) => {
                        if (ips == "") {
                          ips = ipsv4 + "," + ipsv6
                        } else {
                        ips = ips + "," + ipsv4 + "," + ipsv6
                        }
                      }
                      case _ =>
                    }
                }
              }
              jailConfigurations = jailConfigurations ++ List[(String, String, String)]((jid, ips, name))
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }

    (result._1, jailConfigurations)
  }

  def isAlreadyExistingJail(nodeName: String): Boolean = {
    // looking for currently launched jail
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(2000)
    var found = false
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              // already existing jails with the same name
              if (name == nodeName) {
                found = true
              }
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }

    found
  }

  def hasSameConfiguration(nodeName: String, nodeips: List[String]): Boolean = {
    // FIXME check constraints ? or try to update the constraints instead (must be done in JailKevoreeNodeRunner)
    // looking for currently launched jail
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(2000)
    var found = false
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              val resultActor = new ResultManagementActor()
              resultActor.starting()
              currentProcess = Runtime.getRuntime.exec(Array[String]("jls", "-n", "-j", jid))
              new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(jlsRegex), Array(), currentProcess)).start()
              val result = resultActor.waitingFor(2000)
              if (result._1) {
                result._2.split("\n").foreach {
                  line =>
                    line match {
                      case jlsRegex(ipsv4, ipsv6) => {
                        val ips = ipsv4.split(",") ++ ipsv6.split(",")
                        found = nodeips.forall{
                          ip =>
                            ips.exists(p => p == ip)
                        }
                      }
                    }
                }
              }
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }
    found
  }

  def listIpAlreadyUsedByJails(): (Boolean, List[String]) = {
    // looking for currently launched jail
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(2000)
    var ipsList: List[String] = List[String]()
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              ipsList = ipsList ++ List(ip)
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }

    (result._1, ipsList)
  }

  def addNetworkAlias(networkInterface: String, newIps: List[String], mask: String = "24"): Boolean = {
    newIps.forall {
      newIp =>
        val netmask = new SubnetUtils(newIp + "/" + mask).getInfo.getNetmask
        logger.debug("Running {} {} alias {} netmask {}", Array[String](ifconfig, networkInterface, newIp, netmask))
        val resultActor = new ResultManagementActor()
        resultActor.starting()
        currentProcess = Runtime.getRuntime.exec(Array[String](ifconfig, networkInterface, "alias", newIp, "netmask", netmask))
        new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(), Array(new Regex("ifconfig: ioctl \\(SIOCDIFADDR\\): .*")), currentProcess)).start()
        val result = resultActor.waitingFor(1000)
        if (!result._1) {
          logger.debug("Unable to configure alias: {}", result._2)
        }
        result._1
    }

  }

  def createJail(flavor: String, nodeName: String, newIps: List[String], archive: Option[String], timeout: Long): Boolean = {
    var exec = Array[String]()
    if (flavor == null) {
      // TODO add archive attribute and use it to save the jail => the archive must be available from all nodes of the network
      logger.debug("Running {} create {} {}", Array[String](ezjailAdmin, nodeName, newIps.mkString(",")))
      exec = Array[String](ezjailAdmin, "create") ++ Array[String](nodeName, newIps.mkString(","))
    } else {
      // TODO add archive attribute and use it to save the jail => the archive must be available from all nodes of the network
      logger.debug("Running {} create -f {} {} {}", Array[String](ezjailAdmin, flavor, nodeName, newIps.mkString(",")))
      exec = Array[String](ezjailAdmin, "create", "-f") ++ Array[String](flavor) ++ Array[String](nodeName, newIps.mkString(","))
    }

    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = Runtime.getRuntime.exec(exec)
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getErrorStream, Array(), Array(new Regex("^Error.*")), currentProcess)).start()
    val result = resultActor.waitingFor(timeout)
    if (!result._1) {
      logger.debug(result._2)
    }
    result._1
  }

  def findPathForJail(nodeName: String): String = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(1000)
    var jailPath = ""
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              if (name == nodeName) {
                jailPath = path
              }
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }
    jailPath
  }

  def startJail(nodeName: String, timeout: Long): Boolean = {
    logger.debug("Running {} onestart {}", Array[String](ezjailAdmin, nodeName))
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = Runtime.getRuntime.exec(Array[String](ezjailAdmin, "onestart", nodeName))
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getErrorStream, Array(), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(timeout)
    if (!result._1) {
      logger.debug(result._2)
    }
    result._1
  }

  def findJail(nodeName: String): (String, String, String) = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    currentProcess = listJailsProcessBuilder.start()
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(ezjailListRegex), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(1000)
    var jailPath = "-1"
    var jailId = "-1"
    var jailIP = "-1"
    if (result._1) {
      result._2.split("\n").foreach {
        line =>
          line match {
            case ezjailListRegex(tmp, jid, ip, name, path) => {
              if (name == nodeName) {
                jailPath = path
                jailId = jid
                jailIP = ip
              }
            }
            case _ =>
          }
      }
    } else {
      logger.debug(result._2)
    }
    (jailPath, jailId, jailIP)
  }

  def startKevoreeOnJail(jailId: String, ram: String, nodeName: String /*, outFile: File, errFile: File*/ , runner: KevoreeNodeRunner, iaasNode: AbstractNodeType, manageChildKevoreePlatform: Boolean): Boolean = {
    logger.debug("trying to start Kevoree node on jail {} ", nodeName)
    // FIXME java memory properties must define as Node properties
    // Currently the kloud provider only manages PJavaSeNode that hosts the software user configuration
    // It will be better to add a new node hosted by the PJavaSeNode
    var exec = Array[String](jexec, jailId, "/usr/local/bin/java")
    if (ram != null && ram != "N/A") {
      var limit = 0l
      try {
        limit = PropertyConversionHelper.getRAM(ram)
        exec = exec ++ Array[String](/*"-Xms512m", */ "-Xmx" + limit + "m")
      } catch {
        case e: NumberFormatException => logger.warn("Unable to take into account RAM limitation because the value {} is not well defined for {}. Default value used.", Array[String](ram, nodeName))
      }

    }
    exec = exec ++ Array[String](/*"-XX:PermSize=256m", "-XX:MaxPermSize=512m", */ "-Djava.awt.headless=true",
      "-Dnode.name=" + nodeName, "-Dnode.update.timeout=" + System.getProperty("node.update.timeout"),
      "-Dnode.bootstrap=" + File.separator + "root" + File.separator + "bootstrapmodel.kev", "-jar",
      File.separator + "root" + File.separator + "kevoree-runtime.jar")
    logger.debug("trying to launch {} {} {} {} {} {} {} {}", exec)
    val nodeProcess = Runtime.getRuntime.exec(exec)
    /*logger.debug("writing logs about {} on {}", nodeName, outFile.getAbsolutePath)
    new Thread(new ProcessStreamFileLogger(nodeProcess.getInputStream, outFile)).start()
    logger.debug("writing logs about {} on {}", nodeName, errFile.getAbsolutePath)
    new Thread(new ProcessStreamFileLogger(nodeProcess.getErrorStream, errFile)).start()*/
    runner.configureLogFile(iaasNode, nodeProcess)
    if (manageChildKevoreePlatform) {
      try {
        Thread.sleep(1000)
        nodeProcess.exitValue
        false
      } catch {
        case e: IllegalThreadStateException => {
          logger.debug("Platform {} is started", nodeName)
          true
        }
      }
    } else {
      true
    }

  }

  def defineJailConstraints(iaasModel: ContainerRoot, node: ContainerNode): Boolean = {
    JailsConstraintsConfiguration.applyJailConstraints(iaasModel, node)
  }

  def stopJail(nodeName: String, timeout: Long): Boolean = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    logger.debug("Running {} onestop {}", Array[String](ezjailAdmin, nodeName))
    currentProcess = Runtime.getRuntime.exec(Array[String](ezjailAdmin, "onestop", nodeName))
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(timeout)
    if (!result._1) {
      logger.debug(result._2)
    }
    result._1
  }

  def deleteJail(nodeName: String, timeout: Long): Boolean = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    logger.debug("Running {} delete -w {}", Array[String](ezjailAdmin, nodeName))
    currentProcess = Runtime.getRuntime.exec(Array[String](ezjailAdmin, "delete", "-w", nodeName))
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(), Array(), currentProcess)).start()
    val result = resultActor.waitingFor(timeout)
    if (!result._1) {
      logger.debug(result._2)
    }
    result._1
  }

  def deleteNetworkAlias(networkInterface: String, oldIP: String): Boolean = {
    val resultActor = new ResultManagementActor()
    resultActor.starting()
    logger.debug("Running {} {} -alias {}", Array[String](ezjailAdmin, networkInterface, oldIP))
    currentProcess = Runtime.getRuntime.exec(Array[String](ifconfig, networkInterface, "-alias", oldIP))
    new Thread(new ProcessStreamManager(resultActor, currentProcess.getInputStream, Array(), Array(new Regex("ifconfig: ioctl \\(SIOCDIFADDR\\): .*")), currentProcess)).start()
    val result = resultActor.waitingFor(1000)
    if (!result._1) {
      logger.debug(result._2)
    }
    result._1
  }

  def waitProcess() {
    currentProcess.waitFor()
  }
}
