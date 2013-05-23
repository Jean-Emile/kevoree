package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Library;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.log.Log;
import org.webbitserver.WebSocketConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/28/13
 * Time: 10:46 AM
 *
 * This WebSocketGroup do the exact same work as WebSocketGroupEchoer but
 * it adds a handler to queue the push requests to the nodes that have not
 * already established a connection to the master server.
 */
@DictionaryType({
        @DictionaryAttribute(name = "max_queued_model", defaultValue = "150", optional = true, fragmentDependant = false)
})
@Library(name = "JavaSE", names = "Android")
@GroupType
public class WebSocketGroupQueuer extends WebSocketGroupEchoer {

    private static final int DEFAULT_MAX_QUEUED_MODEL = 150;

    private Deque<Map.Entry<String, ContainerRoot>> waitingQueue;
    private int maxQueuedModel;

    @Override
    protected void onServerStart() {
        super.onServerStart();

        try {
            maxQueuedModel = Integer.parseInt(getDictionary().get("max_queued_model").toString());
        } catch (Exception e) {
            maxQueuedModel = DEFAULT_MAX_QUEUED_MODEL;
            Log.warn("\"max_queued_model\" attribute malformed! Using default value {}", DEFAULT_MAX_QUEUED_MODEL+"");
        }

        waitingQueue = new ArrayDeque<Map.Entry<String, ContainerRoot>>(maxQueuedModel);
    }

    @Override
    protected void onMasterServerPushEvent(WebSocketConnection connection,
                                           byte[] msg) {
        // deserialize the model from msg
        ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length - 1); // offset is for the control byte
        ContainerRoot model = KevoreeXmiHelper.instance$.loadCompressedStream(bais);
        updateLocalModel(model);

        // for each node in this group
        Log.debug("Master websocket server is going to broadcast model over {} clients", clients.size()+"");
        Group group = getModelElement();
        for (ContainerNode subNode : group.getSubNodes()) {
            String subNodeName = subNode.getName();
            if (!subNodeName.equals(getNodeName())) {
                // this node is not "me" check if we already have an active connection with him or not
                if (containsNode(subNodeName)) {
                    // we already have an active connection with this client
                    // so lets send the model back
                    getSocketFromNode(subNodeName).send(msg, 1, msg.length - 1); // offset is for the control byte

                } else {
                    // we do not have an active connection with this client
                    // meaning that we have to store the model and wait for
                    // him to connect in order to send the model back
                    Map.Entry<String, ContainerRoot> entry = new AbstractMap.SimpleEntry<String, ContainerRoot>(subNodeName, model);
                    try {
                        waitingQueue.addLast(entry);
                    } catch (IllegalStateException e) {
                        // the queue is full, remove the first added element
                        waitingQueue.pollFirst();

                        // add the element at the end now that we have a free space
                        waitingQueue.addLast(entry);

                    }
                    Log.debug(subNodeName+" is not yet connected to master server. It has been added to waiting queue.");
                }
            }
        }
    }

    @Override
    protected void onMasterServerRegisterEvent(WebSocketConnection connection, String nodeName) {
        super.onMasterServerRegisterEvent(connection, nodeName);

        if (waitingQueueContainsNode(nodeName)) {
            // if we ends up here, it means that this node wasn't connected
            // when a push request was initiated earlier and though it has
            // to get the new model back
            Log.debug(nodeName+" is in the waiting queue, meaning that we have to send the model back to him");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            KevoreeXmiHelper.instance$.saveStream(baos, getAndRemoveModelFromQueue(nodeName));
            connection.send(baos.toByteArray());
        }
    }

    private boolean containsNode(String nodeName) {
        for (String name : clients.values()) {
            if (nodeName.equals(name)) return true;
        }
        return false;
    }

    private boolean waitingQueueContainsNode(String nodeName) {
        for (Map.Entry<String, ContainerRoot> entry : waitingQueue) {
            if (entry.getKey().equals(nodeName)) return true;
        }
        return false;
    }

    private ContainerRoot getAndRemoveModelFromQueue(String nodeName) {
        for (Map.Entry<String, ContainerRoot> entry : waitingQueue) {
            if (entry.getKey().equals(nodeName)) {
                waitingQueue.remove(entry);
                return entry.getValue();
            }
        }
        return null;
    }

    private WebSocketConnection getSocketFromNode(String nodeName) {
        for (Map.Entry<WebSocketConnection, String> entry : clients.entrySet()) {
            if (nodeName.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
