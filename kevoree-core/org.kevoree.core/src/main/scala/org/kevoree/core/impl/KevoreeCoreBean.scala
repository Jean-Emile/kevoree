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
import org.kevoree.framework._
import org.kevoree.framework.context.KevoreeDeployManager
import deploy.PrimitiveCommandExecutionHelper
import org.kevoree.tools.aether.framework.NodeTypeBootstrapHelper
import org.kevoree.cloner.ModelCloner
import org.kevoree.core.basechecker.RootChecker
import java.util.{UUID, Date}
import org.kevoree.api.service.core.handler.{KevoreeModelUpdateException, UUIDModel, ModelListener, KevoreeModelHandlerService}

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

  var models: scala.collection.mutable.ArrayBuffer[ContainerRoot] = new scala.collection.mutable.ArrayBuffer[ContainerRoot]()
  var model: ContainerRoot = KevoreeFactory.eINSTANCE.createContainerRoot
  var lastDate: Date = new Date(System.currentTimeMillis)
  var currentModelUUID: UUID = UUID.randomUUID();

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
    //current model is backed-up
    models.append(model)

    // MAGIC NUMBER ;-) , ONLY KEEP 10 PREVIOUS MODEL
    if (models.size > 15) {
      models = models.drop(5)
      logger.debug("Garbage old previous model")
    }

    //Changes the current model by the new model
    model = c
    currentModelUUID = UUID.randomUUID()
    lastDate = new Date(System.currentTimeMillis)

    //TODO ADD LISTENER

    //Fires the update to listeners
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
  }

  val cloner = new ModelCloner
  val modelChecker = new RootChecker

  // treatment of incoming messages
  def internal_process(msg: Any) = msg match {
    //Returns the collection of previous models
    case PreviousModel() => reply(models)
    //Returns a clone of the current system model
    case LastModel() => {
      reply(cloner.clone(model))
    }
    // returns a clone of the current system model with a companion UUID for concurrency management
    case LastUUIDModel() => {
      reply(UUIDModelImpl(currentModelUUID, cloner.clone(model)))
    }
    //Update the system with the model given in parameter, with the UUID for checking
    case UpdateUUIDModel(prevUUIDModel, targetModel) => {
      if (prevUUIDModel.getUUID.compareTo(currentModelUUID) == 0) {
        //TODO CHECK WITH MODEL SHA-1 HASHCODE
        reply(internal_update_model(targetModel))
      } else {
        reply(false)
      }
    }
    //Updates the system to fit to the new model
    case UpdateModel(pnewmodel) => {
      reply(internal_update_model(pnewmodel))
    }
    case _@unknow => logger
      .warn("unknow message  " + unknow.toString + " - sender" + sender.toString + "-" + this.getClass.getName)
  }


  private def internal_update_model(pnewmodel: ContainerRoot): Boolean = {
    if (pnewmodel == null) {
      logger.error("Asking for update with a NULL model !")
      false
    } else {
      try {

        //Model checking
        val checkResult = modelChecker.check(pnewmodel)
        if (!checkResult.isEmpty) {
          logger.error("There is check failure on update model, update refused !")
          import scala.collection.JavaConversions._
          checkResult.foreach {
            cr =>
              logger.error("error=>" + cr.getMessage + ",objects" + cr.getTargetObjects.mkString(","))
          }
          false
        } else {
          //Model check is OK.

          var newmodel = cloner.clone(pnewmodel)
          //CHECK FOR HARA KIRI
          if (HaraKiriHelper.detectNodeHaraKiri(model, newmodel, getNodeName())) {
            logger.warn("HaraKiri detected , flush platform")
            // Creates an empty model, removes the current node (harakiri)
            newmodel = KevoreeFactory.createContainerRoot
            try {
              // Compare the two models and plan the adaptation
              val adaptationModel = nodeInstance.kompare(model, newmodel)
              if (logger.isDebugEnabled) {//Avoid the loop if the debug is not activated
                logger.debug("Adaptation model size " + adaptationModel.getAdaptations.size)
                adaptationModel.getAdaptations.foreach {
                  adaptation =>
                    logger.debug("primitive " + adaptation.getPrimitiveType.getName)
                }
              }
              //Executes the adaptation
              PrimitiveCommandExecutionHelper.execute(adaptationModel, nodeInstance)

              //Cleanup the local runtime
              KevoreeDeployManager.bundleMapping.foreach {
                bm =>
                  try {
                    logger.debug("Try to cleanup " + bm.bundleId + "," + bm.objClassName + "," + bm.name)
                    KevoreeDeployManager.removeMapping(bm)
                    getBundleContext.getBundle(bm.bundleId).uninstall()
                  } catch {
                    case _@e => logger.debug("Error while cleanup platform ", e)
                  }
              }
              logger.debug("Deploy manager cache size after HaraKiri" + KevoreeDeployManager.bundleMapping.size)

              //end of harakiri
              nodeInstance = null

              //place the current model as an empty model (for backup)
              switchToNewModel(newmodel)

              //prepares for deployment of the new system
              newmodel = cloner.clone(pnewmodel)
            } catch {
              case _@e => {
                logger.error("Error while update ", e)
              }
            }
            logger.debug("End HaraKiri")
          }

          //Checks and bootstrap the node
          checkBootstrapNode(newmodel)
          val milli = System.currentTimeMillis
          logger.debug("Begin update model " + milli)
          var deployResult = true
          try {
            // Compare the two models and plan the adaptation
            val adaptationModel = nodeInstance.kompare(model, newmodel);

            //Execution of the adaptation
            deployResult = PrimitiveCommandExecutionHelper.execute(adaptationModel, nodeInstance)
          } catch {
            case _@e => {
              logger.error("Error while update ", e)
              deployResult = false
            }
          }
          if (deployResult) {
            //Merge previous model on new model for platform model
            //KevoreePlatformMerger.merge(newmodel, model)
            switchToNewModel(newmodel)
            logger.info("Deploy result {}", deployResult)
          } else {
            //KEEP FAIL MODEL
            logger.warn("Failed model")
          }
          val milliEnd = System.currentTimeMillis - milli
          logger.debug("End deploy result=" + deployResult + "-" + milliEnd)
          deployResult
        }
      } catch {
        case _@e =>
          logger.error("Error while update", e)
          false
      }
    }
  }

  /**
   * Returns the current model.
   * @Deprecated : Consider using #getLastUUIDModel for concurrency reasons
   */
  @Deprecated
  override def getLastModel: ContainerRoot = {
    (this !? LastModel()).asInstanceOf[ContainerRoot]
  }

  /**
   * Returns the current model with a unique token
   */
  override def getLastUUIDModel: UUIDModel = {
    (this !? LastUUIDModel()).asInstanceOf[UUIDModel]
  }

  /**
   * Asks for an update of the system to the new system described by the model.
   * @Deprecated Consider using #compareAndSwapModel for concurrency reasons
   */
  @Deprecated
  override def updateModel(model: ContainerRoot) {
    this ! UpdateModel(model)
  }

  /**
   * Asks for an update of the system to fit the model given in parameter.<br/>
   * This method is blocking until the update is done.
   * @Deprecated Consider using #atomicCompareAndSwapModel for concurrency reasons.
   */
  override def atomicUpdateModel(model: ContainerRoot) = {
    (this !? UpdateModel(model))
    lastDate
  }

  /**
   * Compares the UUID of the model and the current UUID to verify that no update occurred in between the moment the model had been delivered and the moment the update is asked.<br/>
   * If OK, updates the system and switches to the new model, asynchronously
   */
  override def compareAndSwapModel(previousModel: UUIDModel, targetModel: ContainerRoot) {
    this ! UpdateUUIDModel(previousModel, targetModel)
  }

  /**
   * Compares the UUID of the model and the current UUID to verify that no update occurred in between the moment the model had been delivered and the moment the update is asked.<br/>
   * If OK, updates the system and switches to the new model, synchronously (blocking)
   */
  override def atomicCompareAndSwapModel(previousModel: UUIDModel, targetModel: ContainerRoot): Date = {
    val result = (this !? UpdateUUIDModel(previousModel, targetModel)).asInstanceOf[Boolean]
    if (!result) {
      throw new KevoreeModelUpdateException //SEND AND EXCEPTION - Compare&Swap fail !
    }
    lastDate
  }

  /**
   * Provides the collection of last models (short system history)
   */
  override def getPreviousModel: java.util.List[ContainerRoot] = {
    import scala.collection.JavaConversions._
    (this !? PreviousModel()).asInstanceOf[scala.collection.mutable.ArrayBuffer[ContainerRoot]].toList
  }

  val listenerActor = new KevoreeListeners
  listenerActor.start()

  override def registerModelListener(listener: ModelListener) {
    listenerActor.addListener(listener)
  }

  override def unregisterModelListener(listener: ModelListener) {
    listenerActor.removeListener(listener)
  }


}
