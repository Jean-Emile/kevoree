package org.kevoree.library.javase.webSocketGrp.channel;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.kevoree.annotation.ChannelTypeFragment;
import org.kevoree.annotation.*;
import org.kevoree.framework.*;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.message.Message;
import org.kevoree.library.javase.webSocketGrp.client.ConnectionTask;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClientHandler;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;
import scala.Option;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/25/13
 * Time: 10:41 AM
 * To change this template use File | Settings | File Templates.
 */
@Library(name = "JavaSE", names = "Android")
@DictionaryType({
        @DictionaryAttribute(name = "port", fragmentDependant = true, optional = true),
        @DictionaryAttribute(name = "replay", defaultValue = "true", vals = {"true", "false"}),
        @DictionaryAttribute(name = "maxQueued", defaultValue = "42")
})
@ChannelTypeFragment
public class WebSocketChannelMasterServer extends AbstractChannelFragment {

    private static final int DEFAULT_MAX_QUEUED = 42;

    protected static final byte REGISTER = 0;
    protected static final byte FORWARD = 1;

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private WebSocketClient client;
    private WebServer server;
    private Integer port = null;
    private BiMap<WebSocketConnection, String> clients;
    private WebSocketClientHandler wsClientHandler;
    private Deque<MessageHolder> waitingQueue;

    @Start
    public void startChannel() throws Exception {
        logger.debug("START DAT CHAN");

        // first of all check if the model is alright
        checkNoMultipleMasterServer();

        // get "maxQueued" from dictionary or DEFAULT_MAX_QUEUED if there is any trouble getting it
        int maxQueued = DEFAULT_MAX_QUEUED;
        try {
            maxQueued = Integer.parseInt(getDictionary().get("maxQueued").toString());
            if (maxQueued < 0) throw new NumberFormatException();

        } catch (NumberFormatException e) {
            logger.error("maxQueued attribute must be a valid positive integer number");
        }

        Object portVal = getDictionary().get("port");
        if (portVal != null) {
            port = Integer.parseInt(portVal.toString().trim());
        }

        if (port != null) {
            // dictionary key "port" is defined so it means that this node wants to be a master server
            // it's ok: this node is gonna be the master server
            clients = HashBiMap.create();
            waitingQueue = new ArrayDeque<MessageHolder>(maxQueued);
            server = WebServers.createWebServer(port);
            server.add("/" +getNodeName()+"/"+getName(), serverHandler);
            server.start();

            logger.debug("Channel WebSocket server started on ws://{}:{}{}", server.getUri().getHost(),
                    server.getPort(), "/"+getNodeName()+"/"+getName());

        } else {
            // we are just a client initiating a connection to master server
            List<URI> uris = getMasterServerURIs();
            wsClientHandler = new WebSocketClientHandler();
            wsClientHandler.setHandler(new ConnectionTask.Handler() {
                @Override
                public void onConnectionSucceeded(WebSocketClient cli) {
                    // connection to master server succeed on one of the different URIs
                    logger.debug("Connection to master server {} succeeded", cli.getURI());

                    // stop all other connection attempts
                    wsClientHandler.stopAllTasks();

                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.write(REGISTER);
                        baos.write(getNodeName().getBytes());
                        cli.send(baos.toByteArray());

                        // keep a pointer on this winner
                        client = cli;

                    } catch (IOException e) {
                        logger.warn("Unable to send registration message to master server");
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // if we end up here, it means that master server just forward
                    // a Message for us, so process it
                    remoteDispatchByte(bytes.array());
                }

                @Override
                public void onKilled() {

                }
            });
            for (URI uri : uris) {
                logger.debug("Add {} to WebSocketClientHandler", uri.toString());
                wsClientHandler.startConnectionTask(uri);
            }
        }
    }

    @Stop
    public void stopChannel() {
        if (server != null) {
            server.stop();
            server = null;
        }

        if (client != null) {
            client.close();
            client = null;
        }

        if (wsClientHandler != null) {
            wsClientHandler.stopAllTasks();
        }
    }

    @Update
    public void updateChannel() throws Exception {
        logger.debug("UPDATE");
        if (client != null) {
            if (!client.getConnection().isOpen()) {
                stopChannel();
                startChannel();
            }
        }

        if (server != null) {
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
                // save the current node in the message if it isn't there already
                if (!message.getPassedNodes().contains(getNodeName())) {
                    message.getPassedNodes().add(getNodeName());
                }

                try {
                    // create a message packet
                    MessagePacket msg = new MessagePacket(remoteNodeName, message);

                    if (server != null) {
                        // we are the master server
                        if (clients.containsValue(remoteNodeName)) {
                            // remoteNode is already connected to master server
                            WebSocketConnection conn = clients.inverse().get(remoteNodeName);
                            conn.send(msg.getByteContent());

                        } else {
                            // remote node has not established a connection with master server yet
                            // or connection has been closed, so putting message in the waiting queue
                            MessageHolder msgHolder = new MessageHolder(remoteNodeName, msg.getByteContent());
                            try {
                                waitingQueue.addFirst(msgHolder);
                            } catch (IllegalStateException e) {
                                // if we end up here the queue is full
                                // so get rid of the oldest message
                                waitingQueue.pollLast();
                                // and add the new one
                                waitingQueue.addFirst(msgHolder);
                            }
                        }

                    } else {
                        // we are a client, so we need to send a message packet to master server
                        // and it will forward the message for us
                        // create a byte array output stream and write a FORWARD control byte first
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.write(FORWARD);

                        // serialize the message packet into the ByteArrayOutputStream
                        ObjectOutput out = new ObjectOutputStream(baos);
                        out.writeObject(msg);
                        out.close();

                        // send data to master server
                        client.send(baos.toByteArray());
                    }

                } catch (IOException e) {
                    logger.debug("Error while sending message to " + remoteNodeName + "-" + remoteChannelName);
                }

                return message;
            }
        };
    }

    protected List<URI> getMasterServerURIs() {
        List<URI> uris = new ArrayList<URI>();

        for (KevoreeChannelFragment kfc : getOtherFragments()) {
            Option<String> portOption = KevoreePropertyHelper.getProperty(getModelElement(), "port", true, kfc.getNodeName());
            if (portOption.isDefined()) {
                int port = Integer.parseInt(portOption.get().trim());
                List<String> ips = getAddresses(kfc.getNodeName());

                for (String ip : ips) {
                    uris.add(getWellFormattedURI(ip, port, kfc.getNodeName(), kfc.getName()));
                }
            }
        }
        return uris;
    }

    protected List<String> getAddresses(String remoteNodeName) {
        List<String> ips = org.kevoree.framework.KevoreePropertyHelper.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
        // if there is no IP defined in node network properties
        // then give it a try locally
        if (ips.isEmpty()) ips.add("127.0.0.1");
        return ips;
    }

    protected URI getWellFormattedURI(String host, int port, String nodeName, String chanName) {
        StringBuilder sb = new StringBuilder();
        sb.append("ws://");
        sb.append(host);
        sb.append(":");
        sb.append(port);
        sb.append("/");
        sb.append(nodeName);
        sb.append("/");
        sb.append(chanName);
        return URI.create(sb.toString());
    }

    private void checkNoMultipleMasterServer() throws MultipleMasterServerException {
        int portPropertyCounter = 0;

        logger.debug("getName: {}, getModelElement(): {}, node: {}", getName(), getModelElement(), getNodeName());

        // check other fragment port property
        for (KevoreeChannelFragment kfc : getOtherFragments()) {
            Option<String> portOption = KevoreePropertyHelper.getProperty(getModelElement(), "port", true, kfc.getNodeName());
            if (portOption.isDefined()) {
                logger.debug("port defined is : "+portOption.get());
                portPropertyCounter++;
            }
        }

        // check my port property
        Option<String> portOption = KevoreePropertyHelper.getProperty(getModelElement(), "port", true, getNodeName());
        if (portOption.isDefined()) portPropertyCounter++;

        if (portPropertyCounter == 0 || portPropertyCounter > 1) {
            throw new MultipleMasterServerException("You are not supposed to give multiple master server for this " +
                    "channel, nor none. Specify one port, and only one, in order for this channel to work properly.");
        }
    }

    private void remoteDispatchByte(byte[] msg) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(msg);
            ObjectInputStream ois = new ObjectInputStream(bis);
            Message mess = (Message) ois.readObject();
            ois.close();
            remoteDispatch(mess);
        } catch (Exception e) {
            logger.warn("Something went wrong while deserializing message in {}", getNodeName());
        }
    }

    private boolean waitingQueueContains(String nodeName) {
        for (MessageHolder msg : waitingQueue) {
            if (msg.getNodeName().equals(nodeName)) return true;
        }
        return false;
    }

    private List<byte[]> getPendingMessages(String nodeName) {
        List<byte[]> pendingList = new ArrayList<byte[]>();

        for (MessageHolder msg : waitingQueue) {
            if (msg.getNodeName().equals(nodeName)) {
                pendingList.add(msg.getData());
                waitingQueue.remove(msg);
            }
        }

        return pendingList;
    }

    private BaseWebSocketHandler serverHandler = new BaseWebSocketHandler() {
        @Override
        public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
            // forward to onMessage(WebSocketConnection, byte[])
            onMessage(connection, msg.getBytes());
        }

        @Override
        public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
            // TODO i am afraid that if a random message starts by "0" byte, then it will
            // TODO be considered as a REGISTER event, so we are kinda trapped :/
            switch (msg[0]) {
                case REGISTER:
                    String nodeName = new String(msg, 1, msg.length-1);
                    if (clients.containsValue(nodeName)) {
                        clients.inverse().get(nodeName).close();
                    }
                    clients.forcePut(connection, nodeName);
                    logger.debug("New registered client \"{}\" from {}", nodeName, connection.httpRequest().remoteAddress());

                    List<byte[]> pendingList = getPendingMessages(nodeName);
                    if (!pendingList.isEmpty()) {
                        logger.debug("Sending pending messages from waiting queue to {} ...", nodeName);
                        for (byte[] data : pendingList) {
                            connection.send(data);
                        }
                    }
                    break;

                case FORWARD:
                    String senderNode = clients.get(connection);

                    try {
                        ByteArrayInputStream bis = new ByteArrayInputStream(msg, 1, msg.length-1);
                        ObjectInputStream ois = new ObjectInputStream(bis);
                        MessagePacket mess = (MessagePacket) ois.readObject();
                        ois.close();

                        if (clients.containsValue(mess.recipient)) {
                            // sending message to recipient
                            WebSocketConnection recipient = clients.inverse().get(mess.recipient);
                            recipient.send(mess.getByteContent());

                        } else {
                            // recipient has not yet established a connection with master server
                            // putting it in the queue
                            // TODO
                        }


                    } catch (Exception e) {
                        logger.debug("Something went wrong while deserializing message from {}", senderNode);
                    }
                    break;

                default:
                    // message recipient is master server
                    // process message
                    remoteDispatchByte(msg);
                    break;
            }
        }

        @Override
        public void onClose(WebSocketConnection connection) throws Exception {
            clients.remove(connection);
        }
    };
}
