package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.library.sky.api.nodeType.AbstractHostNode;
import org.kevoree.library.sky.api.nodeType.HostNode;
import org.kevoree.log.Log;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
 *
 */

@Library(name = "SKY")
@DictionaryType({
        @DictionaryAttribute(name = "idclone", defaultValue = "cloneubuntu") ,
        @DictionaryAttribute(name = "timebeforeshutdown",defaultValue ="10",optional = false) ,
        @DictionaryAttribute(name = "memorylimit",defaultValue ="10",optional = false),
        @DictionaryAttribute(name = "cpushares",defaultValue ="10",optional = false)
})
@NodeType
@PrimitiveCommands(value = {
        @PrimitiveCommand(name = HostNode.ADD_NODE, maxTime = LxcHostNode.ADD_TIMEOUT),
        @PrimitiveCommand(name = HostNode.REMOVE_NODE, maxTime = LxcHostNode.REMOVE_TIMEOUT)
})
public class LxcHostNode extends AbstractHostNode {

    public static final long ADD_TIMEOUT = 300000l;
    public static final long REMOVE_TIMEOUT = 180000l;
    private boolean done = false;
    private LxcManager lxcManager = new LxcManager();

    @Start
    @Override
    public void startNode () {
        super.startNode();

        lxcManager.setClone_id(getDictionary().get("idclone").toString());

        getModelService().registerModelListener(new ModelListener() {
            @Override
            public boolean preUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                return true;
            }

            @Override
            public boolean initUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                return true;
            }

            @Override
            public boolean afterLocalUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                return true;
            }

            @Override
            public void modelUpdated() {

                if(!done){
                    done = true;

                    try
                    {
                        // install scripts
                        lxcManager.install();
                        // check if there is a clone source
                        lxcManager.createClone();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.error("Fatal Kevoree LXC configuration");
                    }

                    if(lxcManager.getContainers().size() > 0){

                        ContainerRoot target = null;
                        // looking for previous containers
                        try {
                            target = lxcManager.buildModelCurrentLxcState(getKevScriptEngineFactory(),getNodeName());

                            getModelService().atomicUpdateModel(target);
                        } catch (Exception e) {
                          Log.error("Getting Current LXC State",e);
                        }

                    }
                }

            }

            @Override
            public void preRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
            }

            @Override
            public void postRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
            }
        });


    }

    @Override
    public KevoreeNodeRunner createKevoreeNodeRunner(String nodeName) {
        return new LxcNodeRunner(nodeName,this);
    }

    public LxcManager getLxcManager() {
        return lxcManager;
    }
}
