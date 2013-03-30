package org.kevoree.library.sky.api

import command.{AddNodeCommand, RemoveNodeCommand}
import nodeType.{HostNode, AbstractHostNode}
import org.kevoreeadaptation.{AdaptationPrimitive, ParallelStep, AdaptationModel}
import org.slf4j.{LoggerFactory, Logger}
import org.kevoree._
import api.PrimitiveCommand
import org.kevoreeadaptation.impl.DefaultKevoreeAdaptationFactory
import scala.collection.JavaConversions._


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 13/12/11
 * Time: 09:19
 *
 * @author Erwan Daubert
 * @version 1.0
 */

object PlanningManager {
  private val logger: Logger = LoggerFactory.getLogger(PlanningManager.getClass)

  def kompare(current: ContainerRoot, target: ContainerRoot, skyNode: AbstractHostNode): AdaptationModel = {
    val factory = new DefaultKevoreeAdaptationFactory
    val adaptationModel: AdaptationModel = factory.createAdaptationModel
    var step: ParallelStep = factory.createParallelStep
    adaptationModel.setOrderedPrimitiveSet(step)
    if (skyNode.isHost) {
      var removeNodeType: AdaptationPrimitiveType = null
      var addNodeType: AdaptationPrimitiveType = null
      current.getAdaptationPrimitiveTypes.foreach {
        primitiveType =>
        //        for (primitiveType <- current.getAdaptationPrimitiveTypesForJ) {
          if (primitiveType.getName == HostNode.REMOVE_NODE) {
            removeNodeType = primitiveType
          }
          else if (primitiveType.getName == HostNode.ADD_NODE) {
            addNodeType = primitiveType
          }
      }
      if (removeNodeType == null || addNodeType == null) {
        target.getAdaptationPrimitiveTypes.foreach {
          primitiveType =>
          //          for (primitiveType <- target.getAdaptationPrimitiveTypesForJ) {
            if (primitiveType.getName == HostNode.REMOVE_NODE) {
              removeNodeType = primitiveType
            }
            else if (primitiveType.getName == HostNode.ADD_NODE) {
              addNodeType = primitiveType
            }
        }
      }
      if (removeNodeType == null) {
        logger.warn("there is no adaptation primitive for {}", HostNode.REMOVE_NODE)
      }
      if (addNodeType == null) {
        logger.warn("there is no adaptation primitive for {}", HostNode.ADD_NODE)
      }
      current.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
        case node: ContainerNode => {
          target.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
            case node1: ContainerNode => {
              node.getHosts.foreach {
                subNode =>
                  node1.findByPath("hosts[" + subNode.getName + "]", classOf[ContainerNode]) match {
                    case null => {
                      logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](HostNode.REMOVE_NODE, subNode.getName))
                      val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                      command.setPrimitiveType(removeNodeType)
                      command.setRef(subNode)
                      val subStep: ParallelStep = factory.createParallelStep
                      subStep.addAdaptations(command)
                      adaptationModel.addAdaptations(command)
                      step.setNextStep(subStep)
                      step = subStep
                    }
                    case subNode1: ContainerNode =>
                  }
              }
            }
            case null => {
              logger.debug("Unable to find the current node on the target model, We remove all the hosted nodes from the current model")
              node.getHosts.foreach {
                subNode =>
                  logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](HostNode.REMOVE_NODE, subNode.getName))
                  val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                  command.setPrimitiveType(removeNodeType)
                  command.setRef(subNode)
                  val subStep: ParallelStep = factory.createParallelStep
                  subStep.addAdaptations(command)
                  adaptationModel.addAdaptations(command)
                  step.setNextStep(subStep)
                  step = subStep
              }
            }
          }
        }
        case null =>
      }

      target.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
        case node: ContainerNode => {
          current.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
            case node1: ContainerNode => {
              node.getHosts.foreach {
                subNode =>
                  node1.findByPath("hosts[" + subNode.getName + "]", classOf[ContainerNode]) match {
                    case null => {
                      logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](HostNode.ADD_NODE, subNode.getName))
                      val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                      command.setPrimitiveType(addNodeType)
                      command.setRef(subNode)
                      val subStep: ParallelStep = factory.createParallelStep
                      subStep.addAdaptations(command)
                      adaptationModel.addAdaptations(command)
                      step.setNextStep(subStep)
                      step = subStep
                    }
                    case subNode1: ContainerNode =>
                  }
              }
            }
            case null => {
              logger.debug("Unable to find the current node on the current model, We add all the hosted nodes from the target model")
              node.getHosts.foreach {
                subNode =>
                  logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](HostNode.ADD_NODE, subNode.getName))
                  val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                  command.setPrimitiveType(addNodeType)
                  command.setRef(subNode)
                  val subStep: ParallelStep = factory.createParallelStep
                  subStep.addAdaptations(command)
                  adaptationModel.addAdaptations(command)
                  step.setNextStep(subStep)
                  step = subStep
              }
            }
          }
        }
        case null =>
      }
    }
    logger.debug("Adaptation model contain {} Host node primitives", adaptationModel.getAdaptations.size)
    val superModel: AdaptationModel = skyNode.superKompare(current, target)
    if (!skyNode.isContainer && isContaining(superModel.getOrderedPrimitiveSet)) {
      throw new Exception("This node is not a container (see \"role\" attribute)")
    }
    adaptationModel.addAllAdaptations(superModel.getAdaptations)
    step.setNextStep(superModel.getOrderedPrimitiveSet)
    logger.debug("Adaptation model contain {} primitives", adaptationModel.getAdaptations.size)

    adaptationModel
  }

  private def isContaining(step: ParallelStep): Boolean = {
    if (step != null) {
      // TODO must be tested
      (step.getAdaptations.exists(adaptation =>
        (adaptation.getPrimitiveType.getName == "UpdateDictionaryInstance" && !adaptation.getRef.isInstanceOf[Group])
          || (adaptation.getPrimitiveType.getName == "AddInstance" && !adaptation.getRef.isInstanceOf[Group])
          || (adaptation.getPrimitiveType.getName == "UpdateInstance" && !adaptation.getRef.isInstanceOf[Group])
          || (adaptation.getPrimitiveType.getName == "RemoveInstance" && !adaptation.getRef.isInstanceOf[Group])
          || (adaptation.getPrimitiveType.getName == "StartInstance" && !adaptation.getRef.isInstanceOf[Group])
          || (adaptation.getPrimitiveType.getName == "StopInstance" && !adaptation.getRef.isInstanceOf[Group]))
        || isContaining(step.getNextStep))
    } else {
      false
    }
  }

  def getPrimitive(adaptationPrimitive: AdaptationPrimitive, skyNode: AbstractHostNode): PrimitiveCommand = {
    logger.debug("ask for primitiveCommand corresponding to {}", adaptationPrimitive.getPrimitiveType.getName)
    var command: PrimitiveCommand = null
    if (adaptationPrimitive.getPrimitiveType.getName == HostNode.REMOVE_NODE) {
      logger.debug("add REMOVE_NODE command on {}", (adaptationPrimitive.getRef.asInstanceOf[ContainerNode]).getName)

      val targetNode = adaptationPrimitive.getRef.asInstanceOf[ContainerNode]
      val targetNodeRoot = adaptationPrimitive.getRef.asInstanceOf[ContainerNode].eContainer.asInstanceOf[ContainerRoot]
      command = new RemoveNodeCommand(targetNodeRoot, targetNode.getName, skyNode)
    }
    else if (adaptationPrimitive.getPrimitiveType.getName == HostNode.ADD_NODE) {
      logger.debug("add ADD_NODE command on {}", (adaptationPrimitive.getRef.asInstanceOf[ContainerNode]).getName)

      val targetNode = adaptationPrimitive.getRef.asInstanceOf[ContainerNode]
      val targetNodeRoot = adaptationPrimitive.getRef.asInstanceOf[ContainerNode].eContainer.asInstanceOf[ContainerRoot]
      command = new AddNodeCommand(targetNodeRoot, targetNode.getName, skyNode)
    }
    if (command == null) {
      command = skyNode.superGetPrimitive(adaptationPrimitive)
    }
    command
  }
}