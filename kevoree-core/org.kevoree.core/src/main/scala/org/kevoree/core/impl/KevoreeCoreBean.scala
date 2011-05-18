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
import org.kevoree.framework.KevoreeActor
import org.kevoree.framework.KevoreeGroup
import org.kevoree.framework.KevoreePlatformHelper
import org.kevoree.framework.KevoreeXmiHelper
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory
import scala.reflect.BeanProperty
import org.kevoree.framework.message._
import scala.actors.Actor
import org.kevoree.api.configuration.ConfigConstants
import java.util.Date
import org.kevoree.api.service.core.handler.{ModelListener, KevoreeModelHandlerService}
import org.eclipse.emf.ecore.util.EcoreUtil
import sun.security.krb5.internal.rcache.ReplayCache

class KevoreeCoreBean extends KevoreeModelHandlerService with KevoreeActor {

  @BeanProperty var configService: ConfigurationService = null
  @BeanProperty var bundleContext: BundleContext = null;
  @BeanProperty var kompareService: org.kevoree.api.service.core.kompare.ModelKompareService = null
  @BeanProperty var deployService: org.kevoree.api.service.adaptation.deploy.KevoreeAdaptationDeployService = null
  @BeanProperty var nodeName: String = ""

  var models: java.util.ArrayList[ContainerRoot] = new java.util.ArrayList()
  var model: ContainerRoot = KevoreeFactory.eINSTANCE.createContainerRoot()

  var lastDate: Date = new Date(System.currentTimeMillis)

  def getLastModification = lastDate

  var logger = LoggerFactory.getLogger(this.getClass);

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
              bundleContext.getService(sr).asInstanceOf[KevoreeGroup].triggerModelUpdate
          }
        }
      }
    }.start()


  }

  override def start: Actor = {
    logger.info("Start event : node name = " + configService.getProperty(ConfigConstants.KEVOREE_NODE_NAME))
    setNodeName(configService.getProperty(ConfigConstants.KEVOREE_NODE_NAME));
    super.start

    //State recovery phase
    val lastModelssaved = bundleContext.getDataFile("lastModel.xmi");
    if (lastModelssaved.length() != 0) {
      /* Load previous state */
      val model = KevoreeXmiHelper.load(lastModelssaved.getAbsolutePath());
      switchToNewModel(model)
    }

    this
  }

  override def stop(): Unit = {

    listenerActor.stop()


    super[KevoreeActor].forceStop
    //TODO CLEAN AND REACTIVATE

    // KevoreeXmiHelper.save(bundleContext.getDataFile("lastModel.xmi").getAbsolutePath(), models.head);
  }

  def internal_process(msg: Any) = msg match {
    case updateMsg: PlatformModelUpdate => KevoreePlatformHelper.updateNodeLinkProp(model, nodeName, updateMsg.targetNodeName, updateMsg.key, updateMsg.value, updateMsg.networkType, updateMsg.weight)
    case PreviousModel() => reply(models)
    case LastModel() => {
      logger.debug("Before get copy model")
      reply(EcoreUtil.copy(model))
      logger.debug("After get Copy model")
    }

    case UpdateModel(pnewmodel) => {
      if (pnewmodel == null) {
        logger.error("Null model")
        reply(false)
      } else {

        try {

          logger.debug("Actor size " + this.mailboxSize)

          logger.debug("Copy Model")
          val newmodel = EcoreUtil.copy(pnewmodel)

          val milli = System.currentTimeMillis
          logger.debug("Begin update model " + milli)
          logger.debug("Before kompare")
          val adaptationModel = kompareService.kompare(model, newmodel, nodeName);
          logger.debug("Before deploy")
          val deployResult = deployService.deploy(adaptationModel, nodeName);



          if (deployResult) {
            //Merge previous model on new model for platform model
            //KevoreePlatformMerger.merge(newmodel, model)
            switchToNewModel(newmodel)
            logger.info("Deploy result " + deployResult)
          } else {
            //KEEP FAIL MODEL

            logger.warn("Failed model")

          }

          val milliEnd = System.currentTimeMillis - milli
          logger.debug("End deploy result=" + deployResult + "-" + milliEnd)

          reply(deployResult)
        } catch {
          case _@e =>
            logger.error("Error while update", e)
            reply(false)
        }


      }
    }
    case _@unknow => logger.warn("unknow message  " + unknow.toString + " - sender" + sender.toString + "-" + this.getClass.getName)
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
    logger.debug("Atomic update end")
    lastDate
  }

  override def getPreviousModel: java.util.List[ContainerRoot] = (this !? PreviousModel()).asInstanceOf[java.util.List[ContainerRoot]]


  val listenerActor = new KevoreeListeners
  listenerActor.start()

  override def registerModelListener(listener: ModelListener) {
    listenerActor.addListener(listener)
  }

  override def unregisterModelListener(listener: ModelListener) {
    listenerActor.removeListener(listener)
  }


}
