package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.basicGossiper.protocol.gossip.Gossip;
import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage;
import org.kevoree.library.javase.INetworkSender;
import org.kevoree.library.javase.basicGossiper.GossiperPeriodic;
import org.kevoree.library.javase.basicGossiper.GossiperProcess;
import org.kevoree.library.javase.basicGossiper.Serializer;
import org.kevoree.library.javase.basicGossiper.group.BasicGossiperGroup;
import org.kevoree.library.javase.basicGossiper.group.DataManagerForGroup;
import org.kevoree.library.javase.basicGossiper.group.GroupScorePeerSelector;
import org.kevoree.library.javase.basicGossiper.group.GroupSerializer;
import org.kevoree.library.javase.conflictSolver.AlreadyPassedPrioritySolver;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.net.NetworkSender;
import org.kevoree.log.Log;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/19/13
 * Time: 9:38 AM
 */
@GroupType
public class WebSocketGossiperGroup extends BasicGossiperGroup {

    private static final byte PUSH = 1;
    private static final byte PULL = 2;

    private GossiperProcess processValue;
    private INetworkSender netSender = new NetworkSender();
    private WebServer server;
    private boolean isRunning = false;

    @Start
    public void start() {
        // gossiper init
        port = Integer.parseInt(getDictionary().get("port").toString());
        Long timeoutLong = Long.parseLong((String) this.getDictionary().get("interval"));
        Serializer serializer = new GroupSerializer(this.getModelService());
        dataManager = new DataManagerForGroup(this.getName(), this.getNodeName(), this.getModelService(), new AlreadyPassedPrioritySolver(getKevScriptEngineFactory()));
        processValue = new GossiperProcess(this, dataManager, serializer, false);
        processValue.setNetSender(netSender);
        selector = new GroupScorePeerSelector(timeoutLong, this.currentCacheModel, this.getNodeName());
        Log.debug("{}: initialize GossiperActor", this.getName());
        actor = new GossiperPeriodic(this, timeoutLong, selector, processValue);
        processValue.start();
        actor.start();

        // starting WebSocket server
        server = WebServers.createWebServer(port);
        server.add("/", serverHandler);
        server.start();
        Log.debug("WebSocket server started on {} ({})", getNodeName(), server.getUri().toString());

        isRunning = true;
    }

    @Stop
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            isRunning = false;
        }
        if (actor != null) {
            actor.stop();
            actor = null;
        }
        if (selector != null) {
            selector = null;
        }
        if (processValue != null) {
            processValue.stop();
            processValue = null;
        }
        if (dataManager != null) {
            dataManager = null;
        }
    }

    @Override
    public void push(ContainerRoot containerRoot, String nodeName) throws Exception {
        // retrieve a list of IP:PORT for the targeted node
        Map<String, Integer> entries = getNodeNetworkDef(containerRoot, nodeName);
        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            // try to send model to each entry, hopefully there is a WebSocket server on one of 'em
            pushTo(containerRoot, URI.create("ws://"+entry.getKey()+":"+entry.getValue()+"/"));
        }
    }

    @Override
    public ContainerRoot pull(String nodeName) throws Exception {
        // retrieve a list of IP:PORT for the targeted node
        Map<String, Integer> entries = getNodeNetworkDef(getModelService().getLastModel(), nodeName);
        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            // try to send a pull request to each node, hopefully there is a WebSocket server on one of 'em
            URI uri = URI.create("ws://"+entry.getKey()+":"+entry.getValue()+"/");
            try {
                return pullFrom(uri);
            } catch (Exception e) {
                Log.warn("Unable to pull model from {} ({}).", nodeName, uri.toString());
            }
        }
        throw new Exception("Unable to pull model from "+nodeName);
    }

    @Override
    public void triggerModelUpdate() {
        if (isRunning) {
            final ContainerRoot modelOption = org.kevoree.library.NodeNetworkHelper.updateModelWithNetworkProperty(this);
            if (modelOption != null) {
                getModelService().unregisterModelListener(this);
                getModelService().atomicUpdateModel(modelOption);
                getModelService().registerModelListener(this);
            }
            isRunning = false;
        }
        broadcast(getModelService().getLastModel());
    }

    @Override
    protected void broadcast(ContainerRoot model) {
        currentCacheModel.set(model);
        for (Object o : currentCacheModel.get().getGroups()) {
            Group g = (Group) o;
            if (g.getName().equals(this.getName())) {
                List<String> peers = new ArrayList<String>(g.getSubNodes().size());
                for (ContainerNode node : g.getSubNodes()) {
                    peers.add(node.getName());
                }
                notifyPeersInternal(peers);
            }
        }
    }

    protected void notifyPeersInternal(List<String> l) {
        KevoreeMessage.Message.Builder messageBuilder = KevoreeMessage.Message.newBuilder().setDestName(getName()).setDestNodeName(getNodeName());
        messageBuilder.setContentClass(Gossip.UpdatedValueNotification.class.getName()).setContent(Gossip.UpdatedValueNotification.newBuilder().build().toByteString());
        for (String peer : l) {
            if (!peer.equals(getNodeName())) {
                int port = parsePortNumber(peer);
                boolean sent = false;
                for (String address : getAddresses(peer)) {
                    sent = processValue.netSender().sendMessage/*Unreliable*/(messageBuilder.build(), new InetSocketAddress(address, port));
                    if (sent) {
                        break;
                    }
                }
                if (!sent) {
                    processValue.netSender().sendMessage/*Unreliable*/(messageBuilder.build(), new InetSocketAddress("127.0.0.1", port));
                }

            }
        }
    }

    /**
     * Push a model to a server defined by the given URI using WebSocket
     * @param model a ContainerRoot model
     * @param uri targeted WebSocket server URI
     */
    private void pushTo(ContainerRoot model, URI uri) {
        // serialize model into an OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KevoreeXmiHelper.instance$.saveCompressedStream(baos, model);
        byte[] data = new byte[baos.size() + 1];
        byte[] serializedModel = baos.toByteArray();
        data[0] = PUSH;
        for (int i = 1; i < data.length; i++) {
            data[i] = serializedModel[i - 1];
        }

        try {
            // send data to the node via WebSocket
            WebSocketClient client = new WebSocketClient(uri);
            client.connectBlocking();
            client.send(data);
            client.close();
        } catch (Exception e) {
            Log.warn("Unable to push model to {}: server may be unreachable", uri.toString());
        }
    }

    /**
     * Tries to pull a model from a node defined by the given URI using WebSocket
     * @param uri targeted WebSocket server URI
     * @return
     */
    private ContainerRoot pullFrom(final URI uri) throws Exception {
        // create an exchanger that will handle model transfer from client thread
        // to this context thread
        final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();

        // create a WebSocket client
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onMessage(ByteBuffer bytes) {
                // if we end up here, it means that the server
                // throws a model back at us in answer from the pull request
                Log.debug("Receiving compressed model...");
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes.array());
                ContainerRoot model = KevoreeXmiHelper.instance$.loadCompressedStream(bais);
                try {
                    exchanger.exchange(model);
                } catch (InterruptedException e) {
                    Log.error("Error while exchanging model from {}",e, uri.toString());
                } finally {
                    // close socket connection in any case
                    close();
                }
            }
        };

        // send PULL request to server
        client.connectBlocking();
        client.send(new byte[] {PULL});

        // client as a 5 seconds window to exchange model
        // otherwise the returned model will be null
        return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns a map of IP:PORT for the given node in the given model
     * @param model model in which data will be looked for
     * @param nodeName target node name
     * @return
     */
    private Map<String, Integer> getNodeNetworkDef(ContainerRoot model, String nodeName) {
        Map<String, Integer> entries = new HashMap<String, Integer>();
        int port = 8000;

        // finding node port
        Group groupOption = model.findByPath("groups[" + getName() + "]", Group.class);
        if (groupOption != null) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(groupOption, "port", true, nodeName);
            if (portOption != null) {
                try {
                    port = Integer.parseInt(portOption);
                } catch (NumberFormatException e) {
                    Log.warn("Attribute \"port\" of {} must be an Integer. Default value ({}) is used", getName(), port+"");
                }
            }
        }

        // find node IPs
        List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(model, nodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());

        if (ips.size() == 0) {
            String defaultIP = "127.0.0.1";
            entries.put(defaultIP, port);
            Log.warn("No IP definition found in {}. Default value {} is used.", nodeName, defaultIP);

        } else {
            // filling entries with IPs & port
            for (String ip : ips) {
                entries.put(ip, port);
            }
        }

        return entries;
    }

    /**
     * This is the process done when the control byte is unknown in serverHandler
     *
     * @param conn WebSocketConnection that delivers the message
     * @param data the message
     */
    protected void onExternalMessageReceived(WebSocketConnection conn, byte[] data) {
        try {
            ByteArrayInputStream stin = new ByteArrayInputStream(data);
            stin.read();
            KevoreeMessage.Message msg = KevoreeMessage.Message.parseFrom(stin);
            Log.debug("Rec Some MSG {}->{}->{}", new String[]{msg.getContentClass(), msg.getDestName(), msg.getDestNodeName()});
            if (!msg.getDestNodeName().equals(getNodeName())) {
                processValue.receiveRequest(msg);
            } else {
                Log.debug("message coming from itself, we don't need to manage it");
            }
        } catch (Exception e) {
            Log.error("", e);
        }
    }


    private BaseWebSocketHandler serverHandler = new BaseWebSocketHandler() {
        @Override
        public void onMessage(WebSocketConnection conn, byte[] msg) throws Throwable {
            switch (msg[0]) {
                case PUSH:
                    Log.debug("Client {} is pushing a model", conn.httpRequest().remoteAddress().toString());

                    // deserialize model from byte array
                    ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length-1);
                    ContainerRoot model = KevoreeXmiHelper.instance$.loadCompressedStream(bais);

                    // update local node model
                    localUpdateModel(model);

                    // broadcast new model to other nodes
                    broadcast(model);
                    break;

                case PULL:
                    Log.debug("Client {} ask for a pull", conn.httpRequest().remoteAddress().toString());

                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    KevoreeXmiHelper.instance$.saveCompressedStream(output, getModelService().getLastModel());
                    conn.send(output.toByteArray());
                    break;

                default:
                    onExternalMessageReceived(conn, msg);
                    break;
            }
        }
    };
}
