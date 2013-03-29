package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Library;
import org.kevoree.framework.KevoreeXmiHelper;
import org.webbitserver.WebSocketConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

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

    private Map<String, ContainerRoot> waitingQueue;
    private int maxQueuedModel;

    @Override
    protected void onServerStart() {
        super.onServerStart();

        waitingQueue = new HashMap<String, ContainerRoot>();

        try {
            maxQueuedModel = Integer.parseInt(getDictionary().get("max_queued_model").toString());
        } catch (Exception e) {
            maxQueuedModel = DEFAULT_MAX_QUEUED_MODEL;
            logger.warn("\"max_queued_model\" attribute malformed! Using default value {}", DEFAULT_MAX_QUEUED_MODEL);
        }
    }

    @Override
    protected void onMasterServerPushEvent(WebSocketConnection connection,
                                           byte[] msg) {
        // deserialize the model from msg
        ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length - 1); // offset is for the control byte
        ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
        updateLocalModel(model);

        // for each node in this group
        logger.debug("Master websocket server is going to broadcast model over {} clients", clients.size());
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
                    if (waitingQueue.size() < maxQueuedModel) {
                        waitingQueue.put(subNodeName, model);
                        logger.debug(subNodeName+" is not yet connected to master server. It has been added to waiting queue.");
                    } else {
                        // TODO get rid of the oldest one and keep that new push request instead
                        logger.warn(
                                "Max queued model number reached. Queueing aborted meaning that {} will not get the model once reconnected!",
                                subNodeName);
                    }
                }
            }
        }
    }

    @Override
    protected void onMasterServerRegisterEvent(WebSocketConnection connection, String nodeName) {
        super.onMasterServerRegisterEvent(connection, nodeName);

        if (waitingQueue.containsKey(nodeName)) {
            // if we ends up here, it means that this node wasn't connected
            // when a push request was initiated earlier and though it has
            // to get the new model back
            logger.debug(nodeName+" is in the waiting queue, meaning that we have to send the model back to him");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            KevoreeXmiHelper.$instance.saveStream(baos, waitingQueue.get(nodeName));
            connection.send(baos.toByteArray());
            waitingQueue.remove(nodeName);
        }
    }

    private boolean containsNode(String nodeName) {
        for (String name : clients.values()) {
            if (nodeName.equals(name)) return true;
        }
        return false;
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
