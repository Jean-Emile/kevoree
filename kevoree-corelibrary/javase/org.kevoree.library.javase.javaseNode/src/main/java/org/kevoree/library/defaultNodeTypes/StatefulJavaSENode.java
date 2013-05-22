package org.kevoree.library.defaultNodeTypes;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.log.Log;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 16/10/12
 * Time: 15:30
 */
@Library(name = "JavaSE")
@DictionaryType({
        @DictionaryAttribute(name = "storageLocation", defaultValue = "", optional = true)
})
@NodeType
public class StatefulJavaSENode extends JavaSENode {

    private StatefulModelListener listener = null;
    private final String property = "java.io.tmpdir";
    private States state = States.bootstrap;
    private enum States {
        bootstrap,networkLink,ready,loading
    }


    class StatefulModelListener implements ModelListener {
        public boolean preUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot1) {
            return true;
        }

        public boolean initUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot1) {
            return true;
        }

        public boolean afterLocalUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot1) {
           return true;
        }

        public void modelUpdated() {
            Log.debug("ModelUpdated(" + StatefulJavaSENode.this.toString() + ")");
            switch (state) {
                case bootstrap: {
                    state=States.networkLink;
                    Log.debug("State Bootstrap -> Links");
                }break;
                case networkLink: {
                    File inputModel  = getLastPersistedModel();
                    if(inputModel.exists()){
                        Log.info("Stateful node ready. Loading last config from " + inputModel.getAbsolutePath());
                        Log.debug("State NetworkLink => Loading");
                        state=States.loading;
                        getModelService().updateModel(KevoreeXmiHelper.instance$.load(inputModel.getAbsolutePath()));
                    } else {
                        state=States.ready;
                        Log.debug("State NetworkLink => Ready");
                        Log.info("Stateful node ready. No stored model found at " + inputModel.getAbsolutePath());
                    }
                }break;
                case loading: {
                    state=States.ready;
                    Log.debug("State Loading => Ready");
                }break;
                case ready: {
                    try{
                        File lastSaved = getLastPersistedModel();
                        if(lastSaved.exists()) {
                            KevoreeXmiHelper.instance$.save(lastSaved.getAbsolutePath() + "." +System.currentTimeMillis()+".kev" ,KevoreeXmiHelper.instance$.load(lastSaved.getAbsolutePath()));
                        }
                       // logger.debug("Stateful node started storage of new model at " + lastSaved.getAbsolutePath());
                        KevoreeXmiHelper.instance$.save(lastSaved.getAbsolutePath(),getModelService().getLastModel());
                        Log.info("Stateful node stored new model at " + lastSaved.getAbsolutePath());
                    } catch(Exception e) {
                        Log.error("Error while saving state",e);
                    }
                }break;
            }
        }

        public void preRollback(ContainerRoot containerRoot, ContainerRoot containerRoot1) {
            Log.debug("PreRollback");
        }

        public void postRollback(ContainerRoot containerRoot, ContainerRoot containerRoot1) {
            Log.debug("POSTRollback");
        }
    }

    @Start
    @Override
    public void startNode() {
        super.startNode();
        listener = new StatefulModelListener();
        getModelService().registerModelListener(listener);
    }

    private File getLastPersistedModel() {
        String baseLocation = "";
        if(getDictionary().get("storageLocation") != null) {
            baseLocation = (String) getDictionary().get("storageLocation");
        } else {
            baseLocation = System.getProperty(property);
        }
        if(!baseLocation.endsWith(File.separator)) {
            baseLocation += File.separator;
        }
        return new File(baseLocation + getNodeName()+".kev");
    }

    @Stop
    @Override
    public void stopNode() {
        getModelService().unregisterModelListener(listener);
        listener = null;
        Log.info("Stateful node stopped");
        super.stopNode();
    }

    @Update
    @Override
    public void updateNode() {
        super.updateNode();
    }


}
