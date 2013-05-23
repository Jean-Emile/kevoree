package org.kevoree.library.javase.webSocketGrp.channel;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.framework.*;
import org.kevoree.framework.message.Message;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.log.Log;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import java.io.*;
import java.net.PortUnreachableException;
import java.net.URI;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/21/13
 * Time: 3:10 PM
 * To change this template use File | Settings | File Templates.
 */
@Library(name = "JavaSE", names = "Android")
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "8000", fragmentDependant = true),
        @DictionaryAttribute(name = "replay", defaultValue = "true", vals = {"true", "false"}),
        @DictionaryAttribute(name = "maxQueued", defaultValue = "42")
})
@ChannelTypeFragment
public class WebSocketChannel extends AbstractChannelFragment {

    private static final String RES_TAG = "/channel/";
    private static final int DEFAULT_PORT = 8000;
    private static final int DEFAULT_MAX_QUEUED = 42;

    private WebServer server;
    private Map<String, WebSocketClient> clients;
    private MessageQueuer queuer;

    // dictionary attributes
    private int port = DEFAULT_PORT;
    private boolean replay = false;

    @Start
    public void startChannel() {
        // get "port" from dictionary or DEFAULT_PORT if there is any trouble getting it
        try {
            port = Integer.parseInt(getDictionary().get("port").toString());
        } catch (NumberFormatException e) {
            Log.error("Port attribute must be a valid integer port number! \"{}\" isn't valid", getDictionary().get("port").toString());
            port = DEFAULT_PORT;
            Log.warn("Using default port {} to proceed channel start...", DEFAULT_PORT+"");
        }

        // get "maxQueued" from dictionary or DEFAULT_MAX_QUEUED if there is any trouble getting it
        int maxQueued = DEFAULT_MAX_QUEUED;
        try {
            maxQueued = Integer.parseInt(getDictionary().get("maxQueued").toString());
            if (maxQueued < 0) throw new NumberFormatException();

        } catch (NumberFormatException e) {
            Log.error("maxQueued attribute must be a valid positive integer port number");
        }

        // initialize active connections map
        clients = new HashMap<String, WebSocketClient>();

        // get replay from dictionary
        replay = Boolean.parseBoolean(getDictionary().get("replay").toString());

        if (replay) {
            // create MessageQueuer with maxQueued attribute
            queuer = new MessageQueuer(maxQueued, clients);
        }

        // initialize web socket server
        server = WebServers.createWebServer(port);
        server.add(RES_TAG+getNodeName(), serverHandler);
        server.start();

        Log.debug("WebSocket server started on {}", server.getUri()+RES_TAG+getNodeName());

        getModelService().registerModelListener(modelListener);
    }

    @Stop
    public void stopChannel() {
        if (server != null) {
            server.stop();
            server = null;
        }

        if (clients != null) {
            for (WebSocketClient cli : clients.values()) {
                cli.close();

            }
            clients.clear();
            clients = null;
        }

        if (queuer != null) {
            queuer.flush();
            queuer = null;
        }
    }

    @Update
    public void updateChannel() {
        int currentPort = port;

        try {
            port = Integer.parseInt(getDictionary().get("port").toString());
        } catch (NumberFormatException e) {
            Log.error("Port attribute must be a valid integer port number! \"{}\" isn't valid", getDictionary().get("port").toString());
            port = DEFAULT_PORT;
            Log.warn("Using default port {} to proceed channel start...", DEFAULT_PORT+"");
        }

        if (currentPort != port) {
            Log.debug("UPDATE DAT CHAN");
            stopChannel();
            startChannel();
        }
    }

    @Override
    public Object dispatch(Message msg) {
        for (KevoreePort p : getBindedPorts()) {
            forward(p, msg);
        }

        for (KevoreeChannelFragment cf : getOtherFragments()) {
            if (!msg.getPassedNodes().contains(cf.getNodeName())) {
                forward(cf, msg);
            }
        }

        return msg;
    }

    @Override
    public ChannelFragmentSender createSender(final String remoteNodeName, final String remoteChannelName) {
        return new ChannelFragmentSender() {
            @Override
            public Object sendMessageToRemote(Message message) {
                Log.debug("createSender: "+remoteNodeName);
                byte[] data = null;

                try {
                    // save the current node in the message if it isn't there already
                    if (!message.getPassedNodes().contains(getNodeName())) {
                        message.getPassedNodes().add(getNodeName());
                    }

                    // serialize the message into a byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutput out = new ObjectOutputStream(baos);
                    out.writeObject(message);
                    data = baos.toByteArray();
                    out.close();

                    // initialize a web socket client
                    WebSocketClient conn = null;

                    // check if there's already a connection with the remote node
                    if (clients.containsKey(remoteNodeName)) {
                        // we already have a connection with this node, use it
                        conn = clients.get(remoteNodeName);
                        if (!conn.getConnection().isOpen()) {
                            clients.remove(remoteNodeName);
                            throw new PortUnreachableException();
                        }

                    } else {
                        // we need to create a new connection with this node
                        conn = createNewConnection(remoteNodeName);
                    }

                    // send data to remote node
                    conn.send(data);

                } catch (PortUnreachableException e) {
                    // if we reach this point it means that we do not succeed
                    // in connecting to a web socket server on the targeted node.
                    // Knowing that, if "replay" is set to "true" we keep tracks of
                    // this message and node to retry the process later.
                    if (replay) {
                        // create the message to add to the queue
                        MessageHolder msg = new MessageHolder(remoteNodeName, data);

                        // add URIs to message
                        int nodePort = parsePortNumber(remoteNodeName);
                        for (String ip : getAddresses(remoteNodeName)) {
                            msg.addURI(getWellFormattedURI(ip, nodePort, remoteNodeName));
                        }

                        // add message to queue
                        queuer.addToQueue(msg);

                    } else {
                        // "replay" is set to "false" just drop that message
                        Log.warn("Unable to reach web socket server on {}, forgetting this send message request...",
                                remoteNodeName);
                    }

                } catch (Exception e) {
                    Log.debug("Error while sending message to " + remoteNodeName + "-" + remoteChannelName);
                }
                return message;
            }
        };
    }

    protected WebSocketClient createNewConnection(final String remoteNodeName) throws Exception {
        int nodePort = parsePortNumber(remoteNodeName);

        for (String nodeIp : getAddresses(remoteNodeName)) {
            Log.debug("Trying to connect to server {}:{} ({}) ...", nodeIp, nodePort+"", remoteNodeName);

            URI uri = getWellFormattedURI(nodeIp, nodePort, remoteNodeName);
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onClose(int code, String reason, boolean flag) {
                    // remove this client from active connections when closing
                    clients.remove(remoteNodeName);
                }
            };

            try {
                // try to connect to server
                if (client.connectBlocking()) {
                    // add this client to the map
                    clients.put(remoteNodeName, client);

                    return client;
                }

            } catch (InterruptedException e) {
                Log.error("Unable to connect to server {}:{} ({})", nodeIp, nodePort+"", remoteNodeName);
            }
        }

        throw new PortUnreachableException("No WebSocket server are reachable on "+remoteNodeName);
    }

    protected List<String> getAddresses(String remoteNodeName) {
        List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
        // if there is no IP defined in node network properties
        // then give it a try locally
        if (ips.isEmpty()) ips.add("127.0.0.1");
        return ips;
    }

    protected int parsePortNumber(String nodeName) {
        String portOption = org.kevoree.framework.KevoreePropertyHelper.instance$.getProperty(getModelElement(), "port", true, nodeName);
        if (portOption != null) {
            try {
                return Integer.parseInt(portOption);
            } catch (NumberFormatException e) {
                Log.warn("Attribute \"port\" of {} is not an Integer", getName());
                return 0;
            }
        } else {
            return DEFAULT_PORT;
        }
    }

    protected URI getWellFormattedURI(String host, int port, String nodeName) {
        StringBuilder sb = new StringBuilder();
        sb.append("ws://");
        sb.append(host);
        sb.append(":");
        sb.append(port);
        sb.append(RES_TAG);
        sb.append(nodeName);
        return URI.create(sb.toString());
    }

    private BaseWebSocketHandler serverHandler = new BaseWebSocketHandler() {
        @Override
        public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
            ByteArrayInputStream bis = new ByteArrayInputStream(msg);
            ObjectInputStream ois = new ObjectInputStream(bis);
            Message mess = (Message) ois.readObject();
            ois.close();
            remoteDispatch(mess);
        }

        @Override
        public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
            // forward to onMessage(WebSocketConnection, byte[])
            onMessage(connection, msg.getBytes());
        }
    };

    private ModelListener modelListener = new ModelListener() {
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
            queuer.updateMessages(new MessageQueuer.MessageUpdater() {
                @Override
                public void updateMessages(Deque<MessageHolder> queue) {
                    for (MessageHolder msg : queue) {
                        // remove all URIs from message
                        msg.clearURIs();

                        // update msg URIs
                        String nodeName = msg.getNodeName();
                        int nodePort = parsePortNumber(nodeName);
                        for (String ip : getAddresses(nodeName)) {
                            msg.addURI(getWellFormattedURI(ip, nodePort, nodeName));
                        }
                    }
                }
            });
        }

        @Override
        public void preRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {

        }

        @Override
        public void postRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {

        }
    };
}
