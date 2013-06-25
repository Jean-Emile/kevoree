package org.kevoree.library.sky.api

import org.kevoree.library.sky.api.nodeType.KevoreeNodeRunnerFactory
import org.kevoree.ContainerRoot

import org.slf4j.{LoggerFactory, Logger}

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 22/09/11
 * Time: 11:46
 *
 * @author Erwan Daubert
 * @version 1.0
 */
class KevoreeNodeManager(kevoreeNodeRunnerFactory: KevoreeNodeRunnerFactory) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var runners = new scala.collection.mutable.ArrayBuffer[KevoreeNodeRunner] with scala.collection.mutable.SynchronizedBuffer[KevoreeNodeRunner]

  def stop() {
    logger.debug("try to stop all nodes")
    runners.foreach {
      runner => runner.stopNode()
    }
    runners = new scala.collection.mutable.ArrayBuffer[KevoreeNodeRunner] with scala.collection.mutable.SynchronizedBuffer[KevoreeNodeRunner]
  }

  def addNode(iaasModel: ContainerRoot, targetChildName: String, targetChildModel: ContainerRoot): Boolean = {
    logger.debug("try to add {}", targetChildName)
    val newRunner = kevoreeNodeRunnerFactory.createKevoreeNodeRunner(targetChildName)
    runners.append(newRunner)

    newRunner.addNode(iaasModel, targetChildModel)
  }


  def startNode(iaasModel: ContainerRoot, targetChildName: String, targetChildModel: ContainerRoot) : Boolean = {
    logger.debug("try to start {}", targetChildName)
    runners.find(runner => runner.nodeName == targetChildName) match {
      case None => true // we do nothing because there is no node with this name
      case Some(runner) => {
        runner.startNode(iaasModel, targetChildModel)
      }
    }
  }

  def stopNode(iaasModel: ContainerRoot, targetChildName: String): Boolean = {
    logger.debug("try to stop {}", targetChildName)
    runners.find(runner => runner.nodeName == targetChildName) match {
      case None => true // we do nothing because there is no node with this name
      case Some(runner) => {
        runner.stopNode()
      }
    }
  }

  def removeNode(iaasModel: ContainerRoot, targetChildName: String): Boolean = {
    logger.debug("try to remove {}", targetChildName)
    runners.find(runner => runner.nodeName == targetChildName) match {
      case None => true // we do nothing because there is no node with this name
      case Some(runner) => {
        runners.remove(runners.indexOf(runner))
        runner.removeNode()
      }
    }
  }
}