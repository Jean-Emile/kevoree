package org.kevoree.library.sky.docker;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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


    public LxcNodeRunner(String nodeName,LxcHostNode iaasNode) {
        super(nodeName);

    }



    @Override
    public boolean startNode(ContainerRoot iaasModel, ContainerRoot childBootStrapModel) {

        ContainerNode node =    iaasModel.findByPath("nodes[" + iaasNode.getName() + "]/hosts[" + nodeName + "]",ContainerNode.class);

        System.out.println(node.getName());
        // create lxc



       return false;
    }

    @Override
    public boolean stopNode() {
      return false;
    }
}
