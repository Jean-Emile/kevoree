package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Library;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.library.javase.webSocketGrp.exception.NoMasterServerFoundException;
import org.kevoree.library.javase.webSocketGrp.exception.NotAMasterServerException;
import org.kevoree.log.Log;
import org.webbitserver.WebSocketConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

/**
 * User: leiko
 * Date: 3/27/13
 * Time: 5:14 PM
 *
 * WebSocketGroup that launches a server on the node fragment if and only if a
 * port is given in the node fragment dictionary attribute. This group requires
 * one and only one node to act as a server (so with a given port in this
 * group's attributes) You can only trigger PUSH events on the master server or
 * the event will be lost into the wild. Once you've pushed a model on the
 * master server, this node fragment will handle model broadcasting on each
 * connected node (so if a node hasn't established a connection to this server
 * he will not get notified)
 */
@Library(name = "JavaSE", names = "Android")
@GroupType
public class WebSocketGroupMasterServer extends AWebSocketGroup {

    @Override
    protected void onClientPush(ContainerRoot model, String targetNodeName)
            throws MultipleMasterServerException, NoMasterServerFoundException, NotAMasterServerException {
        throw new NotAMasterServerException(
                "Push request can only be made on master server node.");
    }

    @Override
    protected void onServerPush(ContainerRoot model, String masterServerNodeName, List<URI> addresses)
            throws MultipleMasterServerException, NoMasterServerFoundException {
        // check user authorization
        if (checkAuth()) {
            // user is authenticated
            // serialize model into an OutputStream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(PUSH);
            KevoreeXmiHelper.instance$.saveCompressedStream(baos, model);
            // send push request
            byte[] data = baos.toByteArray();
            for (URI uri : addresses) {
                if (pushTo(uri, data)) {
                    return;
                }
            }

            // if we end up here, it means that we did not succeed to connect and push
            // model to master server... TODO

        } else {
            // user is not authenticated
            throw new IllegalAccessError("You do not have the right to push a model");
        }
    }

    @Override
    protected ContainerRoot onClientPull(String targetNodeName) throws Exception {
        throw new NotAMasterServerException("Pull request can only be made on master server node.");
    }

    @Override
    protected ContainerRoot onServerPull(final String masterServerNodeName, List<URI> addresses) throws Exception {
        final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();

        for (URI uri : addresses) {
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onMessage(ByteBuffer bytes) {
                    Log.debug("Receiving compressed model...");
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes.array());
                    final ContainerRoot root = KevoreeXmiHelper.instance$.loadCompressedStream(bais);
                    try {
                        exchanger.exchange(root);
                    } catch (InterruptedException e) {
                        Log.error("error while waiting model from "+ masterServerNodeName, e);
                    } finally {
                        close();
                    }
                }
            };

            if (client.connectBlocking()) {
                // connection succeeded
                client.send(new byte[] { PULL });

                // when a request has been sent, there is no reason to send
                // another one to other registered addresses on master server node
                break;

            } else {
                Log.warn("Unable to connect to master server on {} ({})", uri.toString(), masterServerNodeName);
            }
        }

        return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onClientTriggerModelUpdate() {
        Log.debug("onClientTriggerModelUpdate");
    }

    @Override
    protected void onServerTriggerModelUpdate() {
        Log.debug("onServerTriggerModelUpdate");
    }

    @Override
    protected void onMasterServerPushEvent(WebSocketConnection conn, byte[] msg) {
        Log.debug("PUSH: " + conn.httpRequest().remoteAddress() + " asked for a PUSH");
        ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length - 1);
        ContainerRoot model = KevoreeXmiHelper.instance$.loadCompressedStream(bais);
        updateLocalModel(model);

        Log.debug("Master websocket server is going to broadcast model over {} clients", clients.size()+"");
        // broadcasting model to each client
        for (WebSocketConnection wsConn : clients.keySet()) {
            Log.debug("Trying to push model to client " + conn.httpRequest().remoteAddress());
            wsConn.send(msg, 1, msg.length - 1); // offset is for the control byte
        }
    }

    @Override
    protected void onMasterServerCloseEvent(WebSocketConnection conn) {
        String str = "";
        String nodeName;
        if ((nodeName = clients.remove(conn)) != null) {
            str = " => "+nodeName + " removed from active connections.";
        }
        Log.debug("CLOSE: Client "
                + conn.httpRequest().remoteAddress()
                + " closed connection with server" + str);
    }

    @Override
    protected void onMasterServerOpenEvent(WebSocketConnection conn) {
        Log.debug("OPEN: New client opens connection: "
                + conn.httpRequest().remoteAddress());
    }

    @Override
    protected void onMasterServerRegisterEvent(WebSocketConnection conn, String nodeToRegister) {
        clients.put(conn, nodeToRegister);
        Log.debug(
                "REGISTER: New client ({}) added to active connections: {}",
                nodeToRegister, conn.httpRequest().remoteAddress().toString());
        // sending master server nodeName back to client
        conn.send(getNodeName());
    }

    @Override
    protected void onMasterServerPullEvent(WebSocketConnection conn, byte[] msg) {
        Log.debug("PULL: Client " + conn.httpRequest().remoteAddress()
                + " ask for a pull");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KevoreeXmiHelper.instance$.saveCompressedStream(output,
                getModelService().getLastModel());
        conn.send(output.toByteArray());
    }

    protected boolean pushTo(URI address, byte[] data) {
        boolean modelSent = false;

        WebSocketClient client = new WebSocketClient(address);
        try {
            if (client.connectBlocking()) {
                client.send(data);
                modelSent = true;
            }
        } catch (InterruptedException e) {
            Log.error("", e);
        }

        return modelSent;
    }
}
