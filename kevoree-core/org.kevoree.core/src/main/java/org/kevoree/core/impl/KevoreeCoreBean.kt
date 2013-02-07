package org.kevoree.core.impl

import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.core.basechecker.kevoreeVersionChecker.KevoreeNodeVersionChecker
import org.kevoree.ContainerRoot
import org.kevoree.cloner.ModelCloner
import java.util.Date
import org.kevoree.core.basechecker.RootChecker
import java.util.concurrent.ExecutorService
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.ArrayList
import org.kevoree.api.Bootstraper
import java.util.concurrent.Callable
import org.kevoree.api.service.core.handler.UUIDModel
import org.kevoree.api.service.core.handler.ModelUpdateCallback
import org.kevoree.api.service.core.handler.ModelUpdateCallBackReturn
import org.kevoree.api.service.core.handler.ModelHandlerLockCallBack
import java.util.concurrent.ThreadFactory
import org.kevoree.ContainerNode
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.kevoree.api.service.core.handler.KevoreeModelUpdateException
import org.kevoree.api.service.core.handler.ModelListener
import org.kevoree.context.ContextRoot
import org.kevoree.framework.HaraKiriHelper
import org.kevoree.framework.KevoreeXmiHelper
import org.kevoree.impl.DefaultKevoreeFactory


class PreCommand(newmodel : ContainerRoot,modelListeners: KevoreeListeners, oldModel : ContainerRoot){
    var alreadyCall = false
    val preRollbackTest: ()->Boolean = {()->
        if (!alreadyCall) {
            modelListeners.preRollback(oldModel, newmodel)
            alreadyCall = true
        }
        true
    }
}

class KevoreeCoreBean(): KevoreeModelHandlerService {

    val modelListeners = KevoreeListeners()
    var _kevsEngineFactory: KevScriptEngineFactory? = null
    private var modelVersionChecker: KevoreeNodeVersionChecker? = null
    var _bootstraper: Bootstraper? = null
    var _nodeName: String = ""
    var nodeInstance: org.kevoree.api.NodeType? = null
    var models: MutableList<ContainerRoot> = ArrayList<ContainerRoot>()
    val kevoreeFactory = org.kevoree.impl.DefaultKevoreeFactory()
    var model: ContainerRoot = kevoreeFactory.createContainerRoot()
    var lastDate: Date = Date(System.currentTimeMillis())
    var currentModelUUID: UUID = UUID.randomUUID()
    var logger = LoggerFactory.getLogger(this.javaClass)!!
    val modelCloner = ModelCloner()
    val modelChecker = RootChecker()
    var selfActorPointer = this
    private var scheduler: ExecutorService? = null
    private var lockWatchDog: ScheduledExecutorService? = null
    private var futurWatchDog: ScheduledFuture<out Any?>? = null
    private var currentLock: LockCallBack? = null

    data class LockCallBack(val uuid: UUID, val callback: ModelHandlerLockCallBack)


    override fun getNodeName(): String {
        return _nodeName
    }

    fun setNodeName(nn: String) {
        _nodeName = nn
        modelVersionChecker = KevoreeNodeVersionChecker(_nodeName)
    }

    override fun getLastModification(): Date {
        return lastDate
    }

    fun setKevsEngineFactory(k: KevScriptEngineFactory) {
        _kevsEngineFactory = k
    }

    fun setBootstraper(b : Bootstraper){
        _bootstraper = b
    }

    private fun switchToNewModel(c: ContainerRoot) {
        var cc: ContainerRoot? = c
        if(!c.isReadOnly()){
            logger.error("It is not safe to store ReadWrite model")
            cc = modelCloner.clone(c, true)
        } else {
            cc = c
        }

        //current model is backed-up
        models.add(model)
        // TODO : MAGIC NUMBER ;-) , ONLY KEEP 10 PREVIOUS MODEL
        if (models.size > 15) {
            models = models.drop(5) as MutableList<ContainerRoot>
            logger.debug("Garbage old previous model")
        }
        //Changes the current model by the new model
        if(cc != null){
            model = cc!!
            currentModelUUID = UUID.randomUUID()
            lastDate = Date(System.currentTimeMillis())
            //Fires the update to listeners
            modelListeners.notifyAllListener()
        }
    }

    override fun getLastModel(): ContainerRoot {
        return scheduler?.submit(LastModelCallable())?.get()!!
    }

    private inner class LastModelCallable(): Callable<ContainerRoot> {
        override fun call(): ContainerRoot {
            return model
        }
    }

    override fun getLastUUIDModel(): UUIDModel {
        return scheduler?.submit(LastUUIDModelCallable())?.get()!!
    }

    private inner class LastUUIDModelCallable(): Callable<UUIDModel> {
        override fun call(): UUIDModel {
            return UUIDModelImpl(currentModelUUID, model)
        }
    }

    inline fun cloneCurrentModel(pmodel: ContainerRoot?) : ContainerRoot {
        return modelCloner.clone(pmodel!!, true)!!
    }

    override fun updateModel(pmodel: ContainerRoot?): Unit {
        scheduler!!.submit(UpdateModelCallable(cloneCurrentModel(pmodel), null))
    }

    inner class UpdateModelCallable(val model: ContainerRoot, val callback: ModelUpdateCallback?): Callable<Boolean> {
        override fun call(): Boolean {
            var res: Boolean = false
             if (currentLock == null) {
                val internalRes = internal_update_model(model)
                callCallBack(callback, internalRes, null)
                res = internalRes
            } else {
                logger.debug("Core Locked , UUID mandatory")
                callCallBack(callback, false, ModelUpdateCallBackReturn.CAS_ERROR)
                res = false
            }
            return res
        }
    }

    fun callCallBack(callback: ModelUpdateCallback?, sucess: Boolean, res: ModelUpdateCallBackReturn?) {
        if (callback != null) {
            object : Thread(){
                override fun run() {
                    if (res == null) {
                        callback?.modelProcessed(if (sucess) {
                            ModelUpdateCallBackReturn.UPDATED
                        } else {
                            ModelUpdateCallBackReturn.DEPLOY_ERROR
                        })
                    } else {
                        callback?.modelProcessed(res)
                    }
                }
            }.start()
        }
    }

    fun start() {
        if (getNodeName() == "") {
            setNodeName("node0")
        }
        modelListeners.start(getNodeName())
        logger.info("Kevoree Start event : node name = " + getNodeName())
        scheduler = java.util.concurrent.Executors.newSingleThreadExecutor(KevoreeCoreThreadFactory(getNodeName()))
    }

    fun stop() {
        logger.warn("Kevoree Core will be stopped !")
        modelListeners.stop()
        scheduler?.shutdownNow()
        scheduler = null
        if (nodeInstance != null) {
            try {
                val stopModel = checkUnbootstrapNode(model)
                if (stopModel != null) {
                    val adaptationModel = nodeInstance!!.kompare(model, stopModel)
                    adaptationModel.setInternalReadOnly()
                    val afterUpdateTest: () -> Boolean = {() -> true }
                    val rootNode = model.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>())!!
                    org.kevoree.framework.deploy.PrimitiveCommandExecutionHelper.execute(rootNode, adaptationModel, nodeInstance!!, afterUpdateTest, afterUpdateTest, afterUpdateTest)
                } else {
                    logger.error("Unable to use the stopModel !")
                }

            } catch (e: Exception) {
                logger.error("Error while unbootstrap ", e)
            }
            try {
                logger.debug("Call instance stop")
                nodeInstance?.stopNode()
                nodeInstance == null
                _bootstraper?.clear()
            } catch(e: Exception) {
                logger.error("Error while stopping node instance ", e)
            }
        }
        logger.debug("Kevoree core stopped ")
    }

    private fun lockTimeout() {
        scheduler?.submit(LockTimeoutCallable())
    }

    inner class LockTimeoutCallable(): Runnable {
        override fun run() {
            if (currentLock != null) {
                currentLock!!.callback.lockTimeout()
                currentLock = null
                lockWatchDog?.shutdownNow()
                lockWatchDog = null
                futurWatchDog = null
            }
        }
    }

    override fun checkModel(tModel: ContainerRoot?): Boolean {
        val checkResult = modelChecker.check(model)
        return if (checkResult!= null && checkResult.isEmpty()!!) {
            modelListeners.preUpdate(model, cloneCurrentModel(tModel))
        } else {
            false
        }
    }

    override fun updateModel(tmodel: ContainerRoot?, callback: ModelUpdateCallback?) {
        scheduler!!.submit(UpdateModelCallable(cloneCurrentModel(tmodel), callback))
    }

    override fun compareAndSwapModel(previousModel: UUIDModel?, targetModel: ContainerRoot?, callback: ModelUpdateCallback?) {
        scheduler?.submit(CompareAndSwapCallable(previousModel!!, cloneCurrentModel(targetModel), callback))
    }

    inner class AcquireLock(val callBack: ModelHandlerLockCallBack, val timeout: Long): Runnable {
        override fun run() {
            if (currentLock != null) {
                callBack.lockRejected()
            } else {
                val lockUUID = UUID.randomUUID()
                currentLock = LockCallBack(lockUUID, callBack)
                lockWatchDog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                futurWatchDog = lockWatchDog?.schedule(WatchDogCallable(), timeout, TimeUnit.MILLISECONDS)
                callBack.lockAcquired(lockUUID)
            }
        }
    }

    inner class WatchDogCallable(): Runnable {
        override fun run() {
            lockTimeout()
        }
    }

    override fun releaseLock(uuid: UUID?) {
        scheduler?.submit(ReleaseLockCallable(uuid!!))
    }

    inner class ReleaseLockCallable(val uuid: UUID): Runnable {
        override fun run() {
            if (currentLock != null) {
                if (currentLock!!.uuid.compareTo(uuid) == 0) {
                    currentLock = null
                    futurWatchDog?.cancel(true)
                    futurWatchDog = null
                    lockWatchDog?.shutdownNow()
                    lockWatchDog = null
                }
            }
        }
    }

    inner class CompareAndSwapCallable(val previousModel: UUIDModel, val targetModel: ContainerRoot, val callback: ModelUpdateCallback?): Callable<Boolean> {
        override fun call(): Boolean {
            val res: Boolean = if (currentLock != null) {
                if (previousModel?.getUUID()?.compareTo(currentLock!!.uuid) == 0) {
                    val internalRes = internal_update_model(targetModel)
                    callCallBack(callback, internalRes, null)
                    internalRes
                } else {
                    logger.debug("Core Locked , bad UUID " + previousModel.getUUID())
                    callCallBack(callback, false, ModelUpdateCallBackReturn.CAS_ERROR)
                    false //LOCK REFUSED !
                }
            } else {
                //COMMON CHECK
                if (previousModel.getUUID()?.compareTo(currentModelUUID) == 0) {
                    //TODO CHECK WITH MODEL SHA-1 HASHCODE
                    val internalRes = internal_update_model(targetModel)
                    callCallBack(callback, internalRes, null)
                    internalRes
                } else {
                    callCallBack(callback, false, ModelUpdateCallBackReturn.CAS_ERROR)
                    false
                }
            }
            //System.gc()
            return res
        }
    }

    override fun compareAndSwapModel(previousModel: UUIDModel?, targetModel: ContainerRoot?): Unit {
        scheduler?.submit(CompareAndSwapCallable(previousModel!!,cloneCurrentModel(targetModel), null))
    }

    override fun atomicUpdateModel(tmodel: ContainerRoot?): Date? {
        scheduler?.submit(UpdateModelCallable(cloneCurrentModel(tmodel), null))?.get()
        return lastDate
    }

    override fun getPreviousModel(): MutableList<ContainerRoot> {
        return scheduler?.submit(GetPreviousModelCallable())?.get() as MutableList<ContainerRoot>
    }

    private inner class GetPreviousModelCallable(): Callable<List<ContainerRoot>> {
        override fun call(): List<ContainerRoot> {
            return models
        }
    }

    override fun atomicCompareAndSwapModel(previousModel: UUIDModel?, targetModel: ContainerRoot?): Date? {
        val result = scheduler?.submit(CompareAndSwapCallable(previousModel!!, cloneCurrentModel(targetModel), null))?.get()!!
        if (!result) {
            throw KevoreeModelUpdateException() //SEND AND EXCEPTION - Compare&Swap fail !
        }
        return lastDate
    }

    override fun registerModelListener(listener: ModelListener?) {
        modelListeners.addListener(listener!!)
    }

    override fun unregisterModelListener(listener: ModelListener?) {
        modelListeners.removeListener(listener!!)
    }

    override fun getContextModel(): ContextRoot {
        return nodeInstance?.getContextModel()!!
    }


    class RELEASE_LOCK(uuid: UUID){
    }

    override fun acquireLock(callBack: ModelHandlerLockCallBack?, timeout: Long?) {
        scheduler?.submit(AcquireLock(callBack!!, timeout!!))
    }

    private fun checkUnbootstrapNode(currentModel: ContainerRoot): ContainerRoot? {
        try {
            if (nodeInstance != null) {
                val foundNode = currentModel.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>())
                if(foundNode != null){
                    val modelTmp = modelCloner.clone(currentModel)!!
                    modelTmp.removeAllGroups()
                    modelTmp.removeAllHubs()
                    modelTmp.removeAllMBindings()
                    for(node in modelTmp.getNodes()){
                        if(node.getName() != getNodeName()){
                            modelTmp.removeNodes(node)
                        }
                    }
                    modelTmp.getNodes().get(0).removeAllComponents()
                    modelTmp.getNodes().get(0).removeAllHosts()
                    return modelTmp
                } else {
                    logger.error("TypeDef installation fail !")
                    return null
                }
            } else {
                logger.error("node instance is not available on current model !")
                return null
            }
        } catch (e: Exception) {
            logger.error("Error while unbootstraping node instance ", e)
            return null
        }
    }

    private fun checkBootstrapNode(currentModel: ContainerRoot): Unit {
        try {
            if (nodeInstance == null) {
                val foundNode = currentModel.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>())
                if(foundNode != null){
                    nodeInstance = _bootstraper?.bootstrapNodeType(currentModel, getNodeName(), this, _kevsEngineFactory!!)
                    if(nodeInstance!=null){
                        nodeInstance?.startNode()
                        //SET CURRENT MODEL
                        model = modelCloner.clone(currentModel)!!
                        model.removeAllGroups()
                        model.removeAllHubs()
                        model.removeAllMBindings()
                        for(node in model.getNodes()){
                           if(node.getName() != getNodeName()){
                               model.removeNodes(node)
                           }
                        }
                        model.getNodes().get(0).removeAllComponents()
                        model.getNodes().get(0).removeAllHosts()
                    } else {
                        logger.error("TypeDef installation fail !")
                    }

                } else {
                    logger.error("Node instance name " + getNodeName() + " not found in bootstrap model !")
                }
            }
        } catch(e: Exception) {
            logger.error("Error while bootstraping node instance ", e)
            logger.debug(_bootstraper?.getKevoreeClassLoaderHandler()?.getKCLDump())
            try {
                nodeInstance?.stopNode()
            } catch(e: Exception) {
            } finally {
                _bootstraper?.clear()
            }
            nodeInstance = null
        }
    }

    fun internal_update_model(proposedNewModel: ContainerRoot): Boolean {

        if (proposedNewModel.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>()) == null) {
            logger.error("Asking for update with a NULL model or node name ("+getNodeName()+") was not found in target model !")
            return false
        }
        try {
            val readOnlyNewModel = proposedNewModel
            val checkResult = modelChecker.check(readOnlyNewModel)!!
            val versionCheckResult = modelVersionChecker?.check(readOnlyNewModel)!!
            if ( checkResult.size > 0 || versionCheckResult.size > 0) {
                logger.error("There is check failure on update model, update refused !")
                for(cr in checkResult) {
                    logger.error("error=>" + cr?.getMessage() + ",objects" + cr?.getTargetObjects().toString())
                }
                for(cr in versionCheckResult) {
                    logger.error("error=>" + cr?.getMessage() + ",objects" + cr?.getTargetObjects().toString())
                }
                return false
            } else {
                //Model check is OK.
                logger.debug("Before listeners PreCheck !")
                val preCheckResult = modelListeners.preUpdate(model, readOnlyNewModel)
                logger.debug("PreCheck result = " + preCheckResult)
                logger.debug("Before listeners InitUpdate !")
                val initUpdateResult = modelListeners.initUpdate(model, readOnlyNewModel)
                logger.debug("InitUpdate result = " + initUpdateResult)
                   if (preCheckResult && initUpdateResult) {
                       var newmodel = readOnlyNewModel
                       //CHECK FOR HARA KIRI
                       var previousHaraKiriModel: ContainerRoot? = null
                       val hkh = HaraKiriHelper()
                       if (hkh.detectNodeHaraKiri(model, readOnlyNewModel, getNodeName())) {
                           logger.warn("HaraKiri detected , flush platform")
                           previousHaraKiriModel = model
                           // Creates an empty model, removes the current node (harakiri)
                           newmodel = checkUnbootstrapNode(model)!!
                           try {
                               // Compare the two models and plan the adaptation
                               val adaptationModel = nodeInstance!!.kompare(model, newmodel)
                               adaptationModel.setInternalReadOnly()
                               if (logger.isDebugEnabled()){
                                   //Avoid the loop if the debug is not activated
                                   logger.debug("Adaptation model size " + adaptationModel.getAdaptations().size())
                                   for(adaptation in adaptationModel.getAdaptations()) {
                                       logger.debug("primitive " + adaptation.getPrimitiveType()?.getName())
                                   }
                               }
                               //Executes the adaptation
                               val afterUpdateTest: ()->Boolean = {()-> true }
                               val rootNode = newmodel.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>())
                               org.kevoree.framework.deploy.PrimitiveCommandExecutionHelper.execute(rootNode!!, adaptationModel, nodeInstance!!, afterUpdateTest, afterUpdateTest, afterUpdateTest)
                               nodeInstance?.stopNode()
                               //end of harakiri
                               nodeInstance = null
                               _bootstraper?.clear() //CLEAR
                               //place the current model as an empty model (for backup)

                               val backupEmptyModel = kevoreeFactory.createContainerRoot()
                               backupEmptyModel.setInternalReadOnly()
                               switchToNewModel(backupEmptyModel)

                               //prepares for deployment of the new system
                               newmodel = readOnlyNewModel
                           } catch(e: Exception) {
                               logger.error("Error while update ", e)
                           }
                           logger.debug("End HaraKiri")
                       }


                       //Checks and bootstrap the node
                       checkBootstrapNode(newmodel)
                       val milli = System.currentTimeMillis()
                       logger.debug("Begin update model " + milli)
                       var deployResult = true
                       try {
                           // Compare the two models and plan the adaptation
                           logger.info("Comparing models and planning adaptation.")

                           val adaptationModel = nodeInstance!!.kompare(model, newmodel)
                           adaptationModel.setInternalReadOnly()
                           //Execution of the adaptation
                           logger.info("Launching adaptation of the system.")
                           val  afterUpdateTest: ()-> Boolean = {()-> modelListeners.afterUpdate(model, newmodel) }

                           val preCmd = PreCommand(newmodel,modelListeners,model)
                           val postRollbackTest: ()->Boolean = {() -> modelListeners.postRollback(model, newmodel);true }
                           val rootNode = newmodel.findByQuery("nodes[" + getNodeName() + "]", javaClass<ContainerNode>())!!
                           deployResult = org.kevoree.framework.deploy.PrimitiveCommandExecutionHelper.execute(rootNode, adaptationModel, nodeInstance!!, afterUpdateTest, preCmd.preRollbackTest, postRollbackTest)
                       } catch(e: Exception) {
                           logger.error("Error while update ", e)
                           deployResult = false
                       }
                       if (deployResult) {
                           switchToNewModel(newmodel)
                           logger.info("Update sucessfully completed.")
                       } else {
                           //KEEP FAIL MODEL, TODO
                           logger.warn("Update failed")
                           //IF HARAKIRI
                           if (previousHaraKiriModel != null) {
                               internal_update_model(previousHaraKiriModel!!)
                               previousHaraKiriModel = null //CLEAR
                           }
                       }
                       val milliEnd = System.currentTimeMillis() - milli
                       logger.debug("End deploy result=" + deployResult + "-" + milliEnd)
                       return deployResult

                   } else {
                       logger.debug("PreCheck or InitUpdate Step was refused, update aborded !")
                       return false
                   }

            }
        } catch (e: Exception) {
            logger.error("Error while update", e)
            return false
        }
    }

}
