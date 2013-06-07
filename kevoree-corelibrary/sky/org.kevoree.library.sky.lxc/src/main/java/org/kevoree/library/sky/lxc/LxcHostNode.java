package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerRoot;
import org.kevoree.KevoreeFactory;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.api.service.core.script.KevScriptEngine;
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
        @DictionaryAttribute(name = "OS", defaultValue = "ubuntu", vals = {"busybox", "debian", "fedora","opensuse","ubuntu"})
})
@NodeType
@PrimitiveCommands(value = {
        @PrimitiveCommand(name = HostNode.ADD_NODE, maxTime = 120000)
}, values = {HostNode.REMOVE_NODE})
public class LxcHostNode extends AbstractHostNode {


    @Start
    @Override
    public void startNode () {
        super.startNode();
        // query node

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

                try
                {
                    System.out.println("ADD "+LxcManager.getNodes()+" in current model");
                    KevScriptEngine engine =   getKevScriptEngineFactory().createKevScriptEngine();

                    DefaultKevoreeFactory defaultKevoreeFactory = new DefaultKevoreeFactory();

                        engine.append("merge 'mvn:org.kevoree.corelibrary.sky/org.kevoree.library.sky.lxc/"+defaultKevoreeFactory.getVersion()+"'");
                         engine.append("addNode "+getNodeName()+":LxcHostNode");

                    for(String node_child_id :LxcManager.getNodes()){
                        engine.append("addNode "+node_child_id+":LxcHostNode");
                        engine.append("addChild "+node_child_id+"@"+getNodeName());
                        //  String ip = LxcManager.getIP(node_child_id);
                        // todo set ip
                    }

                    System.out.println(engine.getScript());
                    engine.interpret();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }

            @Override
            public void modelUpdated() {



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

}
