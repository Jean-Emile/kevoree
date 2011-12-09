/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kevoree.core.impl

import org.kevoree.KevoreeFactory
import org.kevoree.ContainerRoot
import org.kevoree.api.configuration.ConfigurationService
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory
import scala.reflect.BeanProperty
import org.kevoree.framework.message._
import scala.actors.Actor
import org.kevoree.api.configuration.ConfigConstants
import java.util.Date
import org.kevoree.api.service.core.handler.{ModelListener, KevoreeModelHandlerService}
import org.kevoree.framework._
import org.kevoree.framework.context.KevoreeDeployManager
import deploy.PrimitiveCommandExecutionHelper
import org.kevoree.tools.aether.framework.NodeTypeBootstrapHelper
import org.kevoree.cloner.ModelCloner
import org.kevoree.core.basechecker.RootChecker

class KevoreeCoreBean extends KevoreeModelHandlerService with KevoreeThreadActor {

  @BeanProperty var configService: ConfigurationService = null
  var bundleContext: BundleContext = null;

  def getBundleContext = bundleContext

  def setBundleContext(bc: BundleContext) {
    bundleContext = bc
    KevoreeDeployManager.setBundle(bc.getBundle)
  }


  @BeanProperty var nodeName: String = ""
  @BeanProperty var nodeInstance: AbstractNodeType = null

  var models: java.util.ArrayList[ContainerRoot] = new java.util.ArrayList()
  var model: ContainerRoot = KevoreeFactory.eINSTANCE.createContainerRoot

  var lastDate: Date = new Date(System.currentTimeMillis)

  def getLastModification = lastDate

  var logger = LoggerFactory.getLogger(this.getClass);
  val modelClone = new ModelCloner

  private def checkBootstrapNode(currentModel: ContainerRoot): Unit = {
    try {
      if (nodeInstance == null) {
        currentModel.getNodes.find(n => n.getName == nodeName) match {
          case Some(foundNode) => {
            val bt = new NodeTypeBootstrapHelper
            bt.bootstrapNodeType(currentModel, nodeName, bundleContext) match {
              case Some(ist: AbstractNodeType) => {
                nodeInstance = ist;
                nodeInstance.startNode()

                //SET CURRENT MODEL 

                model = modelClone.clone(currentModel)
                model.removeAllGroups()
                model.removeAllHubs()
                model.removeAllMBindings()
                model.getNodes.filter(n => n.getName != nodeName).foreach {
                  node =>
                    model.removeNodes(node)
                }
                model.getNodes(0).removeAllComponents()
                model.getNodes(0).removeAllHosts()

              }
              case None => logger.error("TypeDef installation fail !")
            }
          }
          case None => logger.error("Node instance name " + nodeName + " not found in bootstrap model !")
        }
      }
    } catch {
      case _@e => logger.error("Error while bootstraping node instance ", e)
    }
  }

  private def checkUnbootstrapNode(currentModel: ContainerRoot): Option[ContainerRoot] = {
    try {
      if (nodeInstance != null) {
        currentModel.getNodes.find(n => n.getName == nodeName) match {
          case Some(foundNode) => {
            val bt = new NodeTypeBootstrapHelper
            bt.bootstrapNodeType(currentModel, nodeName, bundleContext) match {
              case Some(ist: AbstractNodeType) => {

                val modelTmp = modelClone.clone(currentModel)
                modelTmp.removeAllGroups()
                modelTmp.removeAllHubs()
                modelTmp.removeAllMBindings()
                modelTmp.getNodes.filter(n => n.getName != nodeName).foreach {
                  node =>
                    modelTmp.removeNodes(node)
                }
                modelTmp.getNodes(0).removeAllComponents()
                modelTmp.getNodes(0).removeAllHosts()
                Some(modelTmp)

              }
              case None => logger.error("TypeDef installation fail !"); None
            }
          }
          case None => logger.error("Node instance name " + nodeName + " not found in bootstrap model !"); None
        }
      } else {
        logger.error("node instance is not available on current model !")
        None
      }
    } catch {
      case _@e => logger.error("Error while unbootstraping node instance ", e); None
    }


  }


  private def switchToNewModel(c: ContainerRoot) = {
    models.add(model)
    model = c
    lastDate = new Date(System.currentTimeMillis)
    //TODO ADD LISTENER

    new Actor {
      def act() {
        //NOTIFY LOCAL REASONER
        listenerActor.notifyAllListener()
        //NOTIFY GROUP
        val srs = bundleContext.getServiceReferences(classOf[KevoreeGroup].getName, null)
        if (srs != null) {
          srs.foreach {
            sr =>
              bundleContext.getService(sr).asInstanceOf[KevoreeGroup].triggerModelUpdate()
          }
        }
      }
    }.start()
  }

  override def start: Actor = {
    logger.info("Kevoree Start event : node name = " + configService.getProperty(ConfigConstants.KEVOREE_NODE_NAME))
    setNodeName(configService.getProperty(ConfigConstants.KEVOREE_NODE_NAME));
    super.start()

    //State recovery phase
    /*
    val lastModelssaved = bundleContext.getDataFile("lastModel.xmi");
    if (lastModelssaved.length() != 0) {
      /* Load previous state */
      val model = KevoreeXmiHelper.load(lastModelssaved.getAbsolutePath());
      switchToNewModel(model)
    }  */

    this
  }

  override def stop() {

    logger.warn("Kevoree Core will be stopped !")

    listenerActor.stop()

    super[KevoreeThreadActor].forceStop
    //TODO CLEAN AND REACTIVATE

    if (nodeInstance != null) {

      try {
        val stopModel = checkUnbootstrapNode(model)
        if (stopModel.isDefined) {
          val adaptationModel = nodeInstance.kompare(model, stopModel.get);
          val deployResult = PrimitiveCommandExecutionHelper.execute(adaptationModel, nodeInstance)
        } else {
          logger.error("Unable to use the stopModel !")
        }

      } catch {
        case _@e => {
          logger.error("Error while unbootstrap ", e)
        }
      }
      try {
        nodeInstance.stopNode()
        nodeInstance == null
      } catch {
        case _@e => {
          logger.error("Error while stopping node instance ", e)
        }
      }
    }

    logger.debug("Kevoree core stopped ")
    // KevoreeXmiHelper.save(bundleContext.getDataFile("lastModel.xmi").getAbsolutePath(), models.head);
  }

  val cloner = new ModelCloner
  val modelChecker = new RootChecker


  def internal_process(msg: Any) = msg match {
    //case updateMsg: PlatformModelUpdate => KevoreePlatformHelper.updateNodeLinkProp(model, nodeName, updateMsg.targetNodeName, updateMsg.key, updateMsg.value, updateMsg.networkType, updateMsg.weight)
    case PreviousModel() => reply(models)
    case LastModel() => {
      reply(cloner.clone(model))
    }

    case UpdateModel(pnewmodel) => {
      if (pnewmodel == null) {
        logger.error("Update Asked with a Null model")
        reply(false)
      } else {
        val updtIndex = CoreStats.getExecutionIndex
        try {

          //Checks the model conformance
          val checkingStartTime = System.currentTimeMillis()
          val checkResult = modelChecker.check(pnewmodel)
          CoreStats.checking += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - checkingStartTime))

          if (!checkResult.isEmpty) {
            //Checking errors
            logger.error("There is check failure on update model, update refused !")
            import scala.collection.JavaConversions._
            checkResult.foreach {
              cr =>
                logger.error("error=>" + cr.getMessage + ",objects" + cr.getTargetObjects.mkString(","))
            }
            reply(false)

          } else {
            //No checking errors, resume update

            //Cloning the incoming model
            val cloneStartTime = System.currentTimeMillis()
            var newmodel = cloner.clone(pnewmodel)
            CoreStats.cloneMap += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - cloneStartTime))

            //CHECK FOR HARA KIRI
            /*
              HARAKIRI removes the local node type from the system, then resumes the update with the new model
             */
            if (HaraKiriHelper.detectNodeHaraKiri(model, newmodel, getNodeName())) {
              logger.warn("HaraKiri detected , flush platform")
              newmodel = KevoreeFactory.createContainerRoot
              try {

                //Plan the adaptation
                val kompareStartTime = System.currentTimeMillis()
                val adaptationModel = nodeInstance.kompare(model, newmodel);
                CoreStats.kompare += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - kompareStartTime))

                if (logger.isDebugEnabled) {
                  logger.debug("Adaptation model size {}", adaptationModel.getAdaptations.size)
                  adaptationModel.getAdaptations.foreach {
                    adaptation =>
                      logger.debug("primitive {}", adaptation.getPrimitiveType.getName)
                  }
                }

                //Executes the adaptation to unload the system
                val deployStartTime = System.currentTimeMillis()
                PrimitiveCommandExecutionHelper.execute(adaptationModel, nodeInstance)
                CoreStats.deploy += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - deployStartTime))

                //Unloads the bundles
                KevoreeDeployManager.bundleMapping.foreach {
                  bm =>
                    try {
                      logger.debug("Try to cleanup " + bm.bundleId + "," + bm.objClassName + "," + bm.name)
                      KevoreeDeployManager.removeMapping(bm)
                      getBundleContext.getBundle(bm.bundleId).uninstall()
                    } catch {
                      case _@e => logger.warn("Error while cleanup platform ", e)
                    }
                }
                logger.debug("Deploy manager cache size after HaraKiri" + KevoreeDeployManager.bundleMapping.size)

                nodeInstance = null
                switchToNewModel(newmodel)
                val cloneStartTime = System.currentTimeMillis()
                newmodel = cloner.clone(pnewmodel)
                CoreStats.cloneMap.get(CoreStats.getLastExecutionIndex) match {
                  case None => CoreStats.cloneMap += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - cloneStartTime))
                  case Some(delay) => {
                    CoreStats.cloneMap += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - cloneStartTime + delay))
                  }
                }
              } catch {
                case _@e => {
                  logger.error("Error while update ", e)
                }
              }
              logger.debug("End HaraKiri")
            } //END OF HARAKIRI


            checkBootstrapNode(newmodel)

            val updateStartTime = System.currentTimeMillis()
            logger.debug("Begin update model ")
            var deployResult = true
            try {
              val kompareStartTime = System.currentTimeMillis()
              val adaptationModel = nodeInstance.kompare(model, newmodel);
              CoreStats.kompare.get(CoreStats.getLastExecutionIndex) match {
                case None => CoreStats.kompare += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - kompareStartTime))
                case Some(delay) => {
                  CoreStats.kompare += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - kompareStartTime + delay))
                }
              }

              val deployStartTime = System.currentTimeMillis()
              deployResult = PrimitiveCommandExecutionHelper.execute(adaptationModel, nodeInstance)
              CoreStats.deploy.get(CoreStats.getLastExecutionIndex) match {
                case None => CoreStats.deploy += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - deployStartTime))
                case Some(delay) => {
                  CoreStats.deploy += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - deployStartTime + delay))
                }
              }
            } catch {
              case _@e => {
                logger.error("Error while update ", e)
                deployResult = false
              }
            }


            if (deployResult) {
              switchToNewModel(newmodel)
              logger.info("Deploy result " + deployResult)
              //val milliEnd = System.currentTimeMillis - milli
              logger.debug("End deploy result " + deployResult)//) + "-" + milliEnd)
            } else {
              logger.warn("Failed to update the system to the given model.")
            }
            CoreStats.update.get(CoreStats.getLastExecutionIndex) match {
              case None => CoreStats.update += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - updateStartTime))
              case Some(delay) => {
                CoreStats.update += (CoreStats.getLastExecutionIndex -> (System.currentTimeMillis() - updateStartTime + delay))
              }
            }

            reply(deployResult)

          } //END OF UPDATE execution
        } catch {
          case _@e =>
            logger.error("Error while update", e)
            reply(false)
        }
        logger.info(CoreStats.printStats)
      }//END Checked UPDATE

    }//END CASE
    case _@unknow => logger
      .warn("unknow message  " + unknow.toString + " - sender" + sender.toString + "-" + this.getClass.getName)
  }


  override def getLastModel: ContainerRoot = {
    (this !? LastModel()).asInstanceOf[ContainerRoot]
  }

  override def updateModel(model: ContainerRoot) {
    logger.debug("update asked")
    this ! UpdateModel(model)
    logger.debug("update end")

  }

  override def atomicUpdateModel(model: ContainerRoot) = {
    logger.debug("Atomic update asked")
    (this !? UpdateModel(model))
    logger.debug("Atomic up date end")
    lastDate
  }

  override def getPreviousModel: java.util.List[ContainerRoot] = (this !? PreviousModel())
    .asInstanceOf[java.util.List[ContainerRoot]]


  val listenerActor = new KevoreeListeners
  listenerActor.start()

  override def registerModelListener(listener: ModelListener) {
    listenerActor.addListener(listener)
  }

  override def unregisterModelListener(listener: ModelListener) {
    listenerActor.removeListener(listener)
  }


}
