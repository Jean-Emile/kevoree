package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Library;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.library.javase.webSocketGrp.exception.NoMasterServerFoundException;
import org.kevoree.library.javase.webSocketGrp.exception.NotAMasterServerException;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/28/13
 * Time: 10:42 AM
 *
 * This WebSocketGroup do the exact same work as WebSocketGroupMasterServer but it adds
 * more flexibility for push requests.
 * With this group you are not forced to push a model on the master server node. Each node can
 * process the request: if the node is just a client it will forward the push request to the master server
 * and then get the result as an echo back to update its own model.
 */
@Library(name = "JavaSE", names = "Android")
@GroupType
public class WebSocketGroupEchoer extends WebSocketGroupMasterServer {

    @Override
    protected ContainerRoot onClientPull(String targetNodeName) throws Exception {
        return super.onServerPull(getMasterServerNodeName(), getMasterServerURIs());
    }

    @Override
    protected void onClientPush(ContainerRoot model, String targetNodeName)
            throws MultipleMasterServerException, NoMasterServerFoundException, NotAMasterServerException {
        super.onServerPush(model, getMasterServerNodeName(), getMasterServerURIs());
    }
}
