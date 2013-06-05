package org.kevoree.library.sky.lxc;


import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.library.sky.api.KevoreeNodeRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
 * To change this template use File | Settings | File Templates.
 */
public class LxcNodeRunner extends KevoreeNodeRunner {

    private  String nodeName= "";
    private LxcHostNode iaasNode;
    private  LxcManager lxcManager =new LxcManager();

    public LxcNodeRunner(String nodeName,LxcHostNode iaasNode) {
        super(nodeName);
        this.iaasNode = iaasNode;
        this.nodeName = nodeName;
    }

    @Override
    public boolean startNode(ContainerRoot iaasModel, ContainerRoot childBootStrapModel) {
        ContainerNode node =    iaasModel.findByPath("nodes[" + iaasNode.getName() + "]/hosts[" + nodeName + "]", ContainerNode.class);
        String operating_system = KevoreePropertyHelper.instance$.getProperty(node, "OS", false, "") ;


        return    lxcManager.start(nodeName,operating_system,iaasNode,iaasModel);
    }

    @Override
    public boolean stopNode() {
        return   lxcManager.destroy(nodeName);
    }
}
