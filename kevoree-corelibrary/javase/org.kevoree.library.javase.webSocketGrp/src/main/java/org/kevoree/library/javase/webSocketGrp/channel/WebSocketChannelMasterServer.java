package org.kevoree.library.javase.webSocketGrp.channel;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.kevoree.annotation.*;
import org.kevoree.framework.*;
import org.kevoree.framework.message.Message;
import org.kevoree.library.javase.webSocketGrp.client.ConnectionTask;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClientHandler;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.log.Log;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        @DictionaryAttribute(name = "use_queue", defaultValue = "true", vals = {"true", "false"}),
        @DictionaryAttribute(name = "maxQueued", defaultValue = "42")
})
@ChannelTypeFragment
public class WebSocketChannelMasterServer extends AbstractChannelFragment {

    private static final int DEFAULT_MAX_QUEUED = 42;
    protected static final byte REGISTER = 0;
    protected static final byte FORWARD = 1;
    private WebSocketClient client;
    private WebServer server;
    private Integer port = null;
    private BiMap<WebSocketConnection, String> clients;
    private WebSocketClientHandler wsClientHandler;
    private Deque<MessageHolder> waitingQueue;
    private int maxQueued = DEFAULT_MAX_QUEUED;
    private boolean useQueue = true;

    @Start
    public void startChannel() throws Exception {

        // first of all check if the model is alright
        checkNoMultipleMasterServer();

        // get "maxQueued" from dictionary or DEFAULT_MAX_QUEUED if there is any trouble getting it
        try {
            maxQueued = Integer.parseInt(getDictionary().get("maxQueued").toString());
            if (maxQueued < 0) throw new NumberFormatException();

        } catch (NumberFormatException e) {
            Log.error("maxQueued attribute must be a valid positive integer number");
        }

        useQueue = Boolean.parseBoolean(getDictionary().get("use_queue").toString());

        // create queue with that property value
        if (waitingQueue != null) {
            // this is an update, keep old values in the new waiting queue
            Deque<MessageHolder> bkpQueue = new ArrayDeque<MessageHolder>(waitingQueue);
            // create the fresh new waitingQueue
            waitingQueue = new ArrayDeque<MessageHolder>(maxQueued);
            try {
                // try to add all the old values to the new one
                waitingQueue.addAll(bkpQueue);
            } catch (IllegalStateException e) {
                Log.warn("\"maxQueued\" property has been reduced since last update, dropping some pending messages...");
            }
        } else {
            // this is not an update
            waitingQueue = new ArrayDeque<MessageHolder>(maxQueued);
        }


        Object portVal = getDictionary().get("port");
        if (portVal != null) {
            port = Integer.parseInt(portVal.toString().trim());
        }

        if (port != null) {
            // dictionary key "port" is defined so it means that this node wants to be a master server
            // it's ok: this node is gonna be the master server
            clients = HashBiMap.create();
            server = WebServers.createWebServer(port);
            server.add("/" +getNodeName()+"/"+getName(), serverHandler);
            server.start();

            Log.debug("[SERVER] Started on ws://{}:{}{} (use_queue = {}, maxQueued = {})",
                    server.getUri().getHost(), server.getPort()+"", "/"+getNodeName()+"/"+getName(), useQueue+"", maxQueued+"");

        } else {
            // we are just a client initiating a connection to master server
            List<URI> uris = getMasterServerURIs();
            wsClientHandler = new WebSocketClientHandler();
            wsClientHandler.setHandler(new ConnectionTask.Handler() {
                @Override
                public void onConnectionSucceeded(WebSocketClient cli) {
                    // connection to master server succeed on one of the different URIs
                    Log.debug("[CLIENT] Connected to master server {} (use_queue = {}, maxQueued = {})",
                            cli.getURI().toString(), useQueue+"", maxQueued+"");

                    // stop all other connection attempts
                    wsClientHandler.stopAllTasks();

                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.write(REGISTER);
                        baos.write(getNodeName().getBytes());
                        cli.send(baos.toByteArray());

                        // keep a pointer on this winner
                        client = cli;

                        // if use_queue is true, check if there is any message in the waitingQueue
                        if (useQueue && !waitingQueue.isEmpty()) {
                            Log.debug("[CLIENT] {} pending message{}, sending them right now...", waitingQueue.size()+"",
                                    (waitingQueue.size() > 1 ? "s" : ""));
                            Iterator<MessageHolder> it = waitingQueue.iterator();

                            while (it.hasNext()) {
                                // client is connected: sending data to master server
                                MessageHolder msgHolder = it.next();
                                client.send(msgHolder.getData());
                                // remove sent message from queue
                                waitingQueue.remove(msgHolder);
                            }
                        }

                    } catch (IOException e) {
                        Log.warn("Unable to send registration message to master server");
                    }
                }

                @Override
                public void onConnectionClosed(WebSocketClient cli) {
                    Log.debug("Connection has been closed");
                    if (cli.equals(client)) {
                        Log.debug("Gonna try to reconnect to server...");
                        client = null;
                        for (URI uri : getMasterServerURIs()) {
                            wsClientHandler.startConnectionTask(uri);
                        }
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // if we end up here, it means that master server just forward
                    // a Message for us, so process it
                    Log.debug("[CLIENT] New message received");
                    remoteDispatchByte(bytes.array());
                }
            });
            for (URI uri : uris) {
                Log.debug("Add {} to WebSocketClientHandler", uri.toString());
                wsClientHandler.startConnectionTask(uri);
            }
        }
    }

    @Stop
    public void stopChannel() throws Exception {
        if (server != null) {
            server.stop().get(5, TimeUnit.SECONDS);
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
        Log.debug("UPDATE");
        stopChannel();
        startChannel();
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
                synchronized (WebSocketChannelMasterServer.this) {

                    // save the current node in the message if it isn't there already
                    if (!message.getPassedNodes().contains(getNodeName())) {
                        message.getPassedNodes().add(getNodeName());
                    }

                    try {
                        // create a message packet
                        MessagePacket msg = new MessagePacket(remoteNodeName, message);
                        byte[] msgBytes = msg.getByteContent();

                        if (server != null) {
                            // we are the master server
                            if (clients.containsValue(remoteNodeName)) {
                                // remoteNode is already connected to master server
                                WebSocketConnection conn = clients.inverse().get(remoteNodeName);
                                conn.send(msgBytes);

                            } else {
                                // remote node has not established a connection with master server yet
                                // or connection has been closed, so putting message in the waiting queue
                                if (useQueue) {
                                    Log.debug("Message added to queue." +
                                            " {} is not yet connected to master server on {}/{}", remoteNodeName, getName(), getNodeName());
                                    addMessageToQueue(remoteNodeName, msgBytes);
                                } else {
                                    Log.debug("{} is not connected, \"use_queue\" = false : dropping message");
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

                            // check if client is connected
                            if (client != null) {
                                // client is connected: sending data to master server
                                client.send(baos.toByteArray());

                            } else {
                                if (useQueue) {
                                    // client is not connected yet, holding message in a queue
                                    Log.debug("Not connected to master server yet: message to {} added to queue.", remoteNodeName);
                                    addMessageToQueue(remoteNodeName, baos.toByteArray());
                                } else {
                                    Log.debug("Not connected to master and \"use_queue = false\": dropping message");
                                }
                            }
                        }

                    } catch (IOException e) {
                        Log.debug("Error while sending message to {} (remoteChannel: {})", remoteNodeName, remoteChannelName);
                    }
                }

                return message;
            }
        };
    }

    protected List<URI> getMasterServerURIs() {
        List<URI> uris = new ArrayList<URI>();

        for (KevoreeChannelFragment kfc : getOtherFragments()) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(getModelElement(), "port", true, kfc.getNodeName());
            if (portOption != null) {
                int port = Integer.parseInt(portOption.trim());
                List<String> ips = getAddresses(kfc.getNodeName());

                for (String ip : ips) {
                    uris.add(getWellFormattedURI(ip, port, kfc.getNodeName(), kfc.getName()));
                }
            }
        }
        return uris;
    }

    protected List<String> getAddresses(String remoteNodeName) {
        List<String> ips = org.kevoree.framework.KevoreePropertyHelper.instance$.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
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

        // check other fragment port property
        for (KevoreeChannelFragment kfc : getOtherFragments()) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(getModelElement(), "port", true, kfc.getNodeName());
            if (portOption != null) {
                portPropertyCounter++;
            }
        }

        // check my port property
        String portOption = KevoreePropertyHelper.instance$.getProperty(getModelElement(), "port", true, getNodeName());
        if (portOption != null) portPropertyCounter++;

        if (portPropertyCounter == 0) {
            throw new MultipleMasterServerException("You are supposed to give one master server! None found.");
        }

        if (portPropertyCounter > 1) {
            throw new MultipleMasterServerException("You are not supposed to give more or less than one master server! "+portPropertyCounter+" found.");
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
            Log.warn("Something went wrong while deserializing message in {}", getNodeName());
        }
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

    private void addMessageToQueue(String recipientNode, byte[] msgBytes) {
        MessageHolder msgHolder = new MessageHolder(recipientNode, msgBytes);
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

    private BaseWebSocketHandler serverHandler = new BaseWebSocketHandler() {
        @Override
        public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
            // forward to onMessage(WebSocketConnection, byte[])
            onMessage(connection, msg.getBytes());
        }

        @Override
        public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
            switch (msg[0]) {
                case REGISTER:
                    String nodeName = new String(msg, 1, msg.length-1);
                    if (clients.containsValue(nodeName)) {
                        Log.debug("Already got {} in my active connections," +
                                " gonna close the old one, and keep the fresh new", nodeName);
                        clients.inverse().get(nodeName).close();
                        clients.inverse().remove(nodeName);
                    }
                    clients.put(connection, nodeName);
                    Log.debug("New registered client \"{}\" from {}", nodeName, connection.httpRequest().remoteAddress().toString());

                    List<byte[]> pendingList = getPendingMessages(nodeName);
                    if (useQueue && !pendingList.isEmpty()) {
                        Log.debug("Sending pending messages from waiting queue to {} ...", nodeName);
                        for (byte[] data : pendingList) {
                            // we process the data to be sure we were not a client before
                            // because if we were a client, messages in waitingQueue are not
                            // exactly the same as in a waitingQueue of server
                            try {
                                ObjectInput oi = new ObjectInputStream(new ByteArrayInputStream(data));
                                MessagePacket msgPkt = (MessagePacket) oi.readObject();
                                connection.send(msgPkt.getByteContent());

                            } catch (Exception e) {
                                // we were not a client
                                // behave like a server
                                connection.send(data);
                            }
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
                            Log.debug("[SERVER] I know the dude, forwarding message to him...");
                            // sending message to recipient
                            WebSocketConnection recipient = clients.inverse().get(mess.recipient);
                            recipient.send(mess.getByteContent());

                        } else if (mess.recipient.equals(getNodeName())) {
                            Log.debug("[SERVER] oh this is a forward for me actually, dispatching to my face");
                            remoteDispatchByte(mess.getByteContent());


                        } else {
                            if (useQueue) {
                                Log.debug("[SERVER] well, dude is not connected, forward request added to queue...");
                                // recipient has not yet established a connection with master server
                                // putting it in the queue
                                addMessageToQueue(mess.recipient, mess.getByteContent());
                            }
                        }


                    } catch (Exception e) {
                        Log.debug("Something went wrong while deserializing message from {}", senderNode);
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
