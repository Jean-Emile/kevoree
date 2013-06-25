package org.kevoree.library.sky.api

import org.kevoree.library.sky.api.nodeType.CloudNode
import org.kevoreeadaptation.{KevoreeAdaptationFactory, AdaptationPrimitive, ParallelStep, AdaptationModel}
import org.slf4j.{LoggerFactory, Logger}
import org.kevoree._
import org.kevoreeadaptation.impl.DefaultKevoreeAdaptationFactory
import scala.collection.JavaConversions._
import org.kevoree.kompare.{JavaSePrimitive, KevoreeKompareBean}
import org.kevoree.kompare.scheduling.SchedulingWithTopologicalOrderAlgo
import org.kevoree.framework.AbstractNodeType


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 13/12/11
 * Time: 09:19
 *
 * @author Erwan Daubert
 * @version 1.0
 */

class PlanningManager(skyNode: AbstractNodeType) extends KevoreeKompareBean {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def compareModels(current: ContainerRoot, target: ContainerRoot, nodeName: String): AdaptationModel = {
    val factory = new DefaultKevoreeAdaptationFactory
    val adaptationModel: AdaptationModel = factory.createAdaptationModel
    //    var step: ParallelStep = factory.createParallelStep
    //    adaptationModel.setOrderedPrimitiveSet(step)
//    if (skyNode.isHost) {
      var removeNodeType: AdaptationPrimitiveType = null
      var addNodeType: AdaptationPrimitiveType = null
      current.getAdaptationPrimitiveTypes.foreach {
        primitiveType =>
          if (primitiveType.getName == CloudNode.REMOVE_NODE) {
            removeNodeType = primitiveType
          } else if (primitiveType.getName == CloudNode.ADD_NODE) {
            addNodeType = primitiveType
          }
      }
      if (removeNodeType == null || addNodeType == null) {
        target.getAdaptationPrimitiveTypes.foreach {
          primitiveType =>
            if (primitiveType.getName == CloudNode.REMOVE_NODE) {
              removeNodeType = primitiveType
            }
            else if (primitiveType.getName == CloudNode.ADD_NODE) {
              addNodeType = primitiveType
            }
        }
      }
      if (removeNodeType == null) {
        logger.warn("there is no adaptation primitive for {}", CloudNode.REMOVE_NODE)
      }
      if (addNodeType == null) {
        logger.warn("there is no adaptation primitive for {}", CloudNode.ADD_NODE)
      }

      current.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
        case node: ContainerNode => {
          target.findByPath("nodes[" + skyNode.getNodeName + "]", classOf[ContainerNode]) match {
            case node1: ContainerNode => {
              node.getHosts.foreach {
                subNode =>
                  node1.findByPath("hosts[" + subNode.getName + "]", classOf[ContainerNode]) match {
                    case null => {
                      logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](CloudNode.REMOVE_NODE, subNode.getName))
                      val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                      command.setPrimitiveType(removeNodeType)
                      command.setRef(subNode)
                      //                      val subStep: ParallelStep = factory.createParallelStep
                      //                      subStep.addAdaptations(command)
                      adaptationModel.addAdaptations(command)
                      //                      step.setNextStep(subStep)
                      //                      step = subStep
                    }
                    case subNode1: ContainerNode => {
                      if (subNode1.getStarted() != subNode.getStarted()) {
                        if (subNode.getStarted()) {
                          processStopInstance(subNode, adaptationModel, current, factory)
                        } else {
                          processStartInstance(subNode, adaptationModel, current, factory);
                        }
                      }
                    }
                  }
              }
            }
            case null => {
              logger.debug("Unable to find the current node on the target model, We remove all the hosted nodes from the current model")
              node.getHosts.foreach {
                subNode =>
                  logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](CloudNode.REMOVE_NODE, subNode.getName))
                  val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                  command.setPrimitiveType(removeNodeType)
                  command.setRef(subNode)
                  //                  val subStep: ParallelStep = factory.createParallelStep
                  //                  subStep.addAdaptations(command)
                  adaptationModel.addAdaptations(command)
                //                  step.setNextStep(subStep)
                //                  step = subStep
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
                      logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](CloudNode.ADD_NODE, subNode.getName))
                      val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                      command.setPrimitiveType(addNodeType)
                      command.setRef(subNode)
                      //                      val subStep: ParallelStep = factory.createParallelStep
                      //                      subStep.addAdaptations(command)
                      adaptationModel.addAdaptations(command)
                      //                      step.setNextStep(subStep)
                      //                      step = subStep
                    }
                    case subNode1: ContainerNode => {
                      if (subNode1.getStarted() != subNode.getStarted()) {
                        if (subNode.getStarted()) {
                          processStopInstance(subNode, adaptationModel, current, factory)
                        } else {
                          processStartInstance(subNode, adaptationModel, current, factory)
                        }
                      }
                    }
                  }
              }
            }
            case null => {
              logger.debug("Unable to find the current node on the current model, We add all the hosted nodes from the target model")
              node.getHosts.foreach {
                subNode =>
                  logger.debug("add a {} adaptation primitive with {} as parameter", Array[String](CloudNode.ADD_NODE, subNode.getName))
                  val command: AdaptationPrimitive = factory.createAdaptationPrimitive
                  command.setPrimitiveType(addNodeType)
                  command.setRef(subNode)
                  //                  val subStep: ParallelStep = factory.createParallelStep
                  //                  subStep.addAdaptations(command)
                  adaptationModel.addAdaptations(command)
                //                  step.setNextStep(subStep)
                //                  step = subStep
              }
            }
          }
        }
        case null =>
      }
//    }
    logger.debug("Adaptation model contain {} Host node primitives", adaptationModel.getAdaptations.size)
    //    val superModel: AdaptationModel = skyNode.superKompare(current, target)

    val superModel = super.compareModels(current, target, nodeName)

    /*if (!skyNode.isContainer && isContaining(superModel.getOrderedPrimitiveSet)) {
      throw new Exception("This node is not a container (see \"role\" attribute)")
    }*/
    adaptationModel.addAllAdaptations(superModel.getAdaptations)
    //    step.setNextStep(superModel.getOrderedPrimitiveSet)
    logger.debug("Adaptation model contain {} primitives", adaptationModel.getAdaptations.size)
    adaptationModel
  }


  private def processStopInstance(actualInstance: Instance, adaptationModel: AdaptationModel, actualRoot: ContainerRoot, adaptationModelFactory: KevoreeAdaptationFactory) {
    val ccmd2 = adaptationModelFactory.createAdaptationPrimitive()
    ccmd2.setPrimitiveType(actualRoot.findAdaptationPrimitiveTypesByID(JavaSePrimitive.instance$.getStopInstance()))
    ccmd2.setRef(actualInstance)
    adaptationModel.addAdaptations(ccmd2)
  }

  private def processStartInstance(updatedInstance: Instance, adaptationModel: AdaptationModel, updateRoot: ContainerRoot, adaptationModelFactory: KevoreeAdaptationFactory) {
    val ccmd2 = adaptationModelFactory.createAdaptationPrimitive()
    ccmd2.setPrimitiveType(updateRoot.findAdaptationPrimitiveTypesByID(JavaSePrimitive.instance$.getStartInstance()))
    ccmd2.setRef(updatedInstance)
    adaptationModel.addAdaptations(ccmd2)
  }


  override def plan(adaptationModel: AdaptationModel, p2: String): AdaptationModel = {
    if (!adaptationModel.getAdaptations.isEmpty) {

      val adaptationModelFactory = new org.kevoreeadaptation.impl.DefaultKevoreeAdaptationFactory()
      val scheduling = new SchedulingWithTopologicalOrderAlgo()
      nextStep()
      adaptationModel.setOrderedPrimitiveSet(getCurrentStep)

      // STOP child nodes
      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getStopInstance && adapt.getRef.isInstanceOf[ContainerNode]
      })

      nextStep()


      // REMOVE child nodes
      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == CloudNode.REMOVE_NODE
      })

      nextStep()

      //PROCESS STOP
      scheduling.schedule(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getStopInstance
      }, false).foreach {
        p =>
          getStep.addAdaptations(p)
          setStep(adaptationModelFactory.createParallelStep())
          getCurrentStep.setNextStep(getStep)
          setCurrentStep(getStep)
      }
      // REMOVE BINDINGS
      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt =>
          adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getRemoveBinding ||
            adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getRemoveFragmentBinding
      })
      if (!getStep.getAdaptations.isEmpty) {
        setStep(adaptationModelFactory.createParallelStep())
        getCurrentStep.setNextStep(getStep)
        setCurrentStep(getStep)
      }

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getRemoveInstance
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getRemoveType
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getRemoveDeployUnit
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddThirdParty
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getUpdateDeployUnit
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddDeployUnit
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddType
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddInstance
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt =>
          adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddBinding ||
            adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getAddFragmentBinding
      })

      nextStep()

      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getUpdateDictionaryInstance
      })

      nextStep()


      // ADD child nodes
      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == CloudNode.ADD_NODE
      })

      nextStep()

      // START child nodes
      getStep.addAllAdaptations(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getStartInstance && adapt.getRef.isInstanceOf[ContainerNode]
      })

      nextStep()

      var oldStep = getCurrentStep
      //PROCESS START
      scheduling.schedule(adaptationModel.getAdaptations.filter {
        adapt => adapt.getPrimitiveType.getName == JavaSePrimitive.instance$.getStartInstance && !adapt.getRef.isInstanceOf[ContainerNode]
      }, true).foreach {
        p =>
          getStep.addAdaptations(p)
          setStep(adaptationModelFactory.createParallelStep())
          getCurrentStep.setNextStep(getStep)
          oldStep = getCurrentStep
          setCurrentStep(getStep)
      }
      if (getStep.getAdaptations.isEmpty) {
        oldStep.setNextStep(null)
      }
    } else {
      adaptationModel.setOrderedPrimitiveSet(null)
    }
    clearSteps()
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
}