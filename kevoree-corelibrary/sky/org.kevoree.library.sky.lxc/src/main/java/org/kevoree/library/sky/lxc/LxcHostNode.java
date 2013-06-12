package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerRoot;
import org.kevoree.KevoreeFactory;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.api.service.core.script.KevScriptEngine;
import org.kevoree.api.service.core.script.KevScriptEngineException;
import org.kevoree.impl.DefaultKevoreeFactory;
import org.kevoree.library.sky.api.KevoreeNodeManager;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.library.sky.api.nodeType.AbstractHostNode;
import org.kevoree.library.sky.api.nodeType.HostNode;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
 * To change this template use File | Settings | File Templates.
 *
 */

@Library(name = "SKY")
@DictionaryType({
        @DictionaryAttribute(name = "idclone", defaultValue = "cloneubuntu") ,
        @DictionaryAttribute(name = "timebeforeshutdown",defaultValue ="10",optional = false)
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
                return true;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public boolean initUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                return true;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public boolean afterLocalUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                return true;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void modelUpdated() {

                  if(!done){
                         done = true;
                      if(lxcManager.getNodes().size() > 0){

                          ContainerRoot target = null;
                          try {
                              target = lxcManager.buildModelCurrentLxcState(getKevScriptEngineFactory(),getNodeName());

                              getModelService().atomicUpdateModel(target);
                          } catch (IOException e) {
                              e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                          } catch (Exception e) {
                              e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                          }
                      }
                  }



            }

            @Override
            public void preRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void postRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
                //To change body of implemented methods use File | Settings | File Templates.
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
