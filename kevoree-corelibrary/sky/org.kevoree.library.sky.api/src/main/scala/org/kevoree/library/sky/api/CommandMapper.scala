package org.kevoree.library.sky.api

import org.kevoreeadaptation.AdaptationPrimitive
import org.kevoree.library.sky.api.nodeType.{HostNode, AbstractHostNode}
import org.kevoree.api.PrimitiveCommand
import org.kevoree.{ContainerRoot, ContainerNode}
import org.kevoree.library.sky.api.command.{AddNodeCommand, RemoveNodeCommand}
import org.slf4j.{LoggerFactory, Logger}

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 12/06/13
 * Time: 13:02
 *
 * @author Erwan Daubert
 * @version 1.0
 */
class CommandMapper(skyNode: AbstractHostNode) extends org.kevoree.library.defaultNodeTypes.CommandMapper{
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def buildPrimitiveCommand(adaptationPrimitive: AdaptationPrimitive, nodeName : String): PrimitiveCommand = {
    logger.debug("ask for primitiveCommand corresponding to {}", adaptationPrimitive.getPrimitiveType.getName)
    var command: PrimitiveCommand = null
    if (adaptationPrimitive.getPrimitiveType.getName == HostNode.REMOVE_NODE) {
      logger.debug("add REMOVE_NODE command on {}", adaptationPrimitive.getRef.asInstanceOf[ContainerNode].getName)

      val targetNode = adaptationPrimitive.getRef.asInstanceOf[ContainerNode]
      val targetNodeRoot = adaptationPrimitive.getRef.asInstanceOf[ContainerNode].eContainer.asInstanceOf[ContainerRoot]
      command = new RemoveNodeCommand(targetNodeRoot, targetNode.getName, skyNode)
    }
    else if (adaptationPrimitive.getPrimitiveType.getName == HostNode.ADD_NODE) {
      logger.debug("add ADD_NODE command on {}", adaptationPrimitive.getRef.asInstanceOf[ContainerNode].getName)

      val targetNode = adaptationPrimitive.getRef.asInstanceOf[ContainerNode]
      val targetNodeRoot = adaptationPrimitive.getRef.asInstanceOf[ContainerNode].eContainer.asInstanceOf[ContainerRoot]
      command = new AddNodeCommand(targetNodeRoot, targetNode.getName, skyNode)
    }
    if (command == null) {
      command = super.buildPrimitiveCommand(adaptationPrimitive, nodeName)
    }
    command
  }

}
