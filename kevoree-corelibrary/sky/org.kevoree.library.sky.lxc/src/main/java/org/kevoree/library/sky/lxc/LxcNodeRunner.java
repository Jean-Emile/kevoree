package org.kevoree.library.sky.lxc;


import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.log.Log;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
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
    public boolean addNode(ContainerRoot iaasModel, ContainerRoot childBootStrapModel) {
        Log.debug("addNode " + nodeName + "  parent = " + iaasNode.getNodeName());
        ContainerNode node =    iaasModel.findByPath("nodes[" + iaasNode.getName() + "]/hosts[" + nodeName + "]", ContainerNode.class);
        String id_clone = KevoreePropertyHelper.instance$.getProperty(node, "idclone", false, "") ;
        return    lxcManager.create_container(nodeName,id_clone,iaasNode,iaasModel);
    }

    @Override
    public boolean startNode(ContainerRoot iaasModel, ContainerRoot childBootStrapModel) {
        Log.debug("startNode " + nodeName + "  parent = " + iaasNode.getNodeName());
        return    lxcManager.start_container(nodeName,iaasNode,iaasModel);
    }

    @Override
    public boolean stopNode() {
        return   lxcManager.stop_container(nodeName);
    }

    @Override
    public boolean removeNode() {
        return   lxcManager.remove_container(nodeName);
    }
}
