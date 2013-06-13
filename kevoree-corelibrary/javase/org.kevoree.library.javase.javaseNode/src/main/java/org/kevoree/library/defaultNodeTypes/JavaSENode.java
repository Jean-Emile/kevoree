/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kevoree.library.defaultNodeTypes;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.api.service.core.logging.KevoreeLogLevel;
import org.kevoree.framework.AbstractNodeType;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.kcl.KevoreeJarClassLoader;
import org.kevoree.kompare.KevoreeKompareBean;
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager;
import org.kevoree.log.Log;
import org.kevoreeadaptation.AdaptationModel;
import org.kevoreeadaptation.AdaptationPrimitive;

import java.io.File;


/**
 * @author ffouquet
 */
@Library(name = "JavaSE")
@NodeType
@DictionaryType({
        @DictionaryAttribute(name = "logLevel", defaultValue = "INFO", optional = true, vals = {"INFO", "WARN", "DEBUG", "ERROR", "FINE"}),
        @DictionaryAttribute(name = "coreLogLevel", defaultValue = "WARN", optional = true, vals = {"INFO", "WARN", "DEBUG", "ERROR", "FINE"})
})
@PrimitiveCommands(
        values = {"UpdateType", "AddType", "AddThirdParty", "RemoveType", "RemoveDeployUnit", "UpdateInstance", "UpdateBinding", "UpdateDictionaryInstance", "AddInstance", "RemoveInstance", "AddBinding", "RemoveBinding", "AddFragmentBinding", "RemoveFragmentBinding", "UpdateFragmentBinding", "StartInstance", "StopInstance", "StartThirdParty", "RemoveThirdParty"},
        value = {@PrimitiveCommand(name="AddDeployUnit",maxTime = 120000),@PrimitiveCommand(name="UpdateDeployUnit",maxTime = 120000)})
public class JavaSENode extends AbstractNodeType implements ModelListener {

    protected KevoreeKompareBean kompareBean = null;
    protected CommandMapper mapper = null;

    @Start
    @Override
    public void startNode() {
        mapper = new CommandMapper();
        preTime = System.currentTimeMillis();
        getModelService().registerModelListener(this);
        kompareBean = new KevoreeKompareBean();
        mapper.setNodeType(this);
        updateNode();
    }

    @Stop
    @Override
    public void stopNode() {
        getModelService().unregisterModelListener(this);
        kompareBean = null;
        mapper = null;
        //Cleanup the local runtime
        KevoreeDeployManager.instance$.clearAll(this);
    }

    @Update
    @Override
    public void updateNode() {
        if (getBootStrapperService().getKevoreeLogService() != null) {
            KevoreeLogLevel logLevel = KevoreeLogLevel.WARN;
            KevoreeLogLevel corelogLevel = KevoreeLogLevel.WARN;

            String logLevelVal = (String)getDictionary().get("logLevel");
            if ("DEBUG".equals(logLevelVal)) {
                logLevel = KevoreeLogLevel.DEBUG;
            } else if ("WARN".equals(logLevelVal)) {
                logLevel = KevoreeLogLevel.WARN;
            } else if ("INFO".equals(logLevelVal)) {
                logLevel = KevoreeLogLevel.INFO;
            } else if ("ERROR".equals(logLevelVal)) {
                logLevel = KevoreeLogLevel.ERROR;
            } else if ("FINE".equals(logLevelVal)) {
                logLevel = KevoreeLogLevel.FINE;
            }

            String coreLogLevelVal = (String)getDictionary().get("coreLogLevel");
            if ("DEBUG".equals(coreLogLevelVal)) {
                corelogLevel = KevoreeLogLevel.DEBUG;
            } else if ("WARN".equals(coreLogLevelVal)) {
                corelogLevel = KevoreeLogLevel.WARN;
            } else if ("INFO".equals(coreLogLevelVal)) {
                corelogLevel = KevoreeLogLevel.INFO;
            } else if ("ERROR".equals(coreLogLevelVal)) {
                corelogLevel = KevoreeLogLevel.ERROR;
            } else if ("FINE".equals(coreLogLevelVal)) {
                corelogLevel = KevoreeLogLevel.FINE;
            }
            getBootStrapperService().getKevoreeLogService().setUserLogLevel(logLevel);
            getBootStrapperService().getKevoreeLogService().setCoreLogLevel(corelogLevel);
        }
    }

    @Override
    public AdaptationModel kompare(ContainerRoot current, ContainerRoot target) {
        return kompareBean.kompare(current, target, this.getNodeName());
    }

    @Override
    public org.kevoree.api.PrimitiveCommand getPrimitive(AdaptationPrimitive adaptationPrimitive) {
        return mapper.buildPrimitiveCommand(adaptationPrimitive, this.getNodeName());
    }

    private Long preTime = 0l;

    @Override
    public boolean preUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        preTime = System.currentTimeMillis();
        Log.info("JavaSENode received a new Model to apply...");
        return true;
    }

    @Override
    public boolean initUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        return true;
    }

    @Override
    public boolean afterLocalUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        mapper.doEnd();
        Log.info("JavaSENode V2 Update completed in {} ms",(System.currentTimeMillis() - preTime)+"");
        return true;
    }

    @Override
    public void modelUpdated() {
    }

	@Override
	public void preRollback (ContainerRoot containerRoot, ContainerRoot containerRoot1) {
        Log.warn("JavaSENode is aborting last update...");

        if (Log.DEBUG && this.getClass().getClassLoader() instanceof KevoreeJarClassLoader) {
            Log.error("Dump before rollback:\n" + getBootStrapperService().getKevoreeClassLoaderHandler().getKCLDump());
        }
	}

	@Override
	public void postRollback (ContainerRoot containerRoot, ContainerRoot containerRoot1) {
        Log.warn("JavaSENode update aborted in {} ms",(System.currentTimeMillis() - preTime)+"");
        try {
            File preModel = File.createTempFile("pre"+System.currentTimeMillis(),"pre");
            File afterModel = File.createTempFile("post"+System.currentTimeMillis(),"post");
            KevoreeXmiHelper.instance$.save(preModel.getAbsolutePath(),containerRoot);
            KevoreeXmiHelper.instance$.save(afterModel.getAbsolutePath(),containerRoot1);
            Log.error("PreModel->"+preModel.getAbsolutePath());
            Log.error("PostModel->"+afterModel.getAbsolutePath());
            if (Log.DEBUG && this.getClass().getClassLoader() instanceof KevoreeJarClassLoader) {
                Log.error("Dump after rollback:\n" + getBootStrapperService().getKevoreeClassLoaderHandler().getKCLDump());
            }
        } catch (Exception e){
            Log.error("Error while saving debug model",e);
        }

	}
}
