package org.kevoree.library.javase.webSocketGrp.channel;

import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractChannelFragment;
import org.kevoree.framework.ChannelFragmentSender;
import org.kevoree.framework.KevoreeChannelFragment;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.message.Message;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;
import scala.Option;

import java.io.*;
import java.net.PortUnreachableException;
import java.net.URI;
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
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "8000", fragmentDependant = true),
        @DictionaryAttribute(name = "replay", defaultValue = "false", vals = {"true", "false"}),
        @DictionaryAttribute(name = "maxQueued", defaultValue = "42")
})
@ChannelTypeFragment
public class WebSocketChannel extends AbstractChannelFragment {

    private static final int DEFAULT_PORT = 8000;
    private static final int DEFAULT_MAX_QUEUED = 42;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

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
            logger.error("Port attribute must be a valid integer port number! \"{}\" isn't valid", getDictionary().get("port").toString());
            port = DEFAULT_PORT;
            logger.warn("Using default port {} to proceed channel start...", DEFAULT_PORT);
        }

        // get "maxQueued" from dictionary or DEFAULT_MAX_QUEUED if there is any trouble getting it
        int maxQueued = DEFAULT_MAX_QUEUED;
        try {
            maxQueued = Integer.parseInt(getDictionary().get("maxQueued").toString());
            if (maxQueued < 0) throw new NumberFormatException();

        } catch (NumberFormatException e) {
            logger.error("maxQueued attribute must be a valid positive integer port number");
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
        server.add("/", serverHandler);
        server.start();
    }

    @Stop
    public void stopChannel() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Update
    public void updateChannel() {
        int currentPort = port;

        try {
            port = Integer.parseInt(getDictionary().get("port").toString());
        } catch (NumberFormatException e) {
            logger.error("Port attribute must be a valid integer port number! \"{}\" isn't valid", getDictionary().get("port").toString());
            port = DEFAULT_PORT;
            logger.warn("Using default port {} to proceed channel start...", DEFAULT_PORT);
        }

        if (currentPort != port) {
            stopChannel();
            startChannel();
        }
    }

    @Override
    public Object dispatch(Message msg) {
        for (org.kevoree.framework.KevoreePort p : getBindedPorts()) {
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
                        try {
                            // create the message to add to the queue
                            MessageHolder msg = new MessageHolder(remoteNodeName, data);

                            // add URIs to message
                            int nodePort = parsePortNumber(remoteNodeName);
                            for (String ip : getAddresses(remoteNodeName)) {
                                StringBuilder scheme = new StringBuilder();
                                scheme.append("ws://");
                                scheme.append(ip+":");
                                scheme.append(nodePort+"/");
                                msg.addURI(URI.create(scheme.toString()));
                            }

                            // add message to queue
                            queuer.addToQueue(msg);
                        } catch (IOException ioEx) {
                            logger.error("", ioEx);
                        }

                    } else {
                        // "replay" is set to "false" just drop that message
                        logger.warn("Unable to reach web socket server on {}, forgetting this send message request...",
                                remoteNodeName);
                    }

                } catch (Exception e) {
                    logger.debug("Error while sending message to " + remoteNodeName + "-" + remoteChannelName);
                }
                return message;
            }
        };
    }

    protected WebSocketClient createNewConnection(final String remoteNodeName) throws Exception {
        int nodePort = parsePortNumber(remoteNodeName);

        for (String nodeIp : getAddresses(remoteNodeName)) {
            logger.debug("Trying to connect to server {}:{} ({}) ...", nodeIp, nodePort, remoteNodeName);

            URI uri = URI.create("ws://"+nodeIp+":"+nodePort+"/");
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onClose(int code, String reason, boolean flag) {
                    // remove this client from active connections when closing
                    clients.remove(remoteNodeName);
                }
            };

            try {
                // connect to server
                client.connectBlocking();
                // add this client to the map
                clients.put(remoteNodeName, client);

                return client;

            } catch (InterruptedException e) {
                logger.error("Unable to connect to server {}:{} ({})", nodeIp, nodePort, remoteNodeName);
            }
        }

        throw new PortUnreachableException("No WebSocket server are reachable on "+remoteNodeName);
    }

    protected List<String> getAddresses(String remoteNodeName) {
        return KevoreePropertyHelper.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
    }

    protected int parsePortNumber(String nodeName) throws IOException {
        try {
            Option<String> portOption = org.kevoree.framework.KevoreePropertyHelper.getProperty(getModelElement(), "port", true, nodeName);
            if (portOption.isDefined()) {
                try {
                    return Integer.parseInt(portOption.get());
                } catch (NumberFormatException e) {
                    logger.warn("Attribute \"port\" of {} is not an Integer", getName());
                    return 0;
                }
            } else {
                return DEFAULT_PORT;
            }
        } catch (NumberFormatException e) {
            throw new IOException(e.getMessage());
        }
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
}
