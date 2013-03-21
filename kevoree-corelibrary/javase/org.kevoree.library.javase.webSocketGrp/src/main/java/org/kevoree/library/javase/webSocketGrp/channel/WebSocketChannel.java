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
        @DictionaryAttribute(name = "port", defaultValue = "8000", fragmentDependant = true)
})
@ChannelTypeFragment
public class WebSocketChannel extends AbstractChannelFragment {

    private static final int DEFAULT_PORT = 8000;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private WebServer server;
    private Map<String, WebSocketClient> clients;
    private int port = -1;

    @Start
    public void startChannel() {
        try {
            port = Integer.parseInt(getDictionary().get("port").toString());
        } catch (NumberFormatException e) {
            logger.error("Port attribute must be a valid integer port number! \"{}\" isn't valid", getDictionary().get("port").toString());
            port = DEFAULT_PORT;
            logger.warn("Using default port {} to proceed channel start...", DEFAULT_PORT);
        }

        // initialize active connections map
        clients = new HashMap<String, WebSocketClient>();

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
                try {
                    // save the current node in the message if it isn't there already
                    if (!message.getPassedNodes().contains(getNodeName())) {
                        message.getPassedNodes().add(getNodeName());
                    }

                    // serialize the message into a byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutput out = new ObjectOutputStream(baos);
                    out.writeObject(message);
                    byte[] data = baos.toByteArray();
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
                client.connectBlocking();
                return client;

            } catch (InterruptedException e) {
                logger.error("Unable to connect to server {}:{} ({})", nodeIp, nodePort, remoteNodeName);
            }
        }
        throw new Exception("No WebSocket server are reachable on "+remoteNodeName);
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
