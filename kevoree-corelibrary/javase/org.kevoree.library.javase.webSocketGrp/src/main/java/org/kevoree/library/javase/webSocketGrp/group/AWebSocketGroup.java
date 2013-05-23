package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.DeployUnit;
import org.kevoree.Group;
import org.kevoree.annotation.*;
import org.kevoree.api.service.core.classloading.DeployUnitResolver;
import org.kevoree.framework.AbstractGroupType;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.javase.webSocketGrp.client.ConnectionTask;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClientHandler;
import org.kevoree.library.javase.webSocketGrp.dummy.KeyChecker;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.library.javase.webSocketGrp.exception.NoMasterServerFoundException;
import org.kevoree.library.javase.webSocketGrp.exception.NotAMasterServerException;
import org.kevoree.log.Log;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;
import org.webbitserver.handler.StaticFileHandler;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * <br/>
 * Abstract WebSocket Group
 * <br/>
 * Here you have all the work related to processing differences between master server and client.
 * In order to do that, this class will parse the current model and check if there is any mistakes
 * according to the way WebSocketGroup should work (one and only one master server in this case)
 * <br/>
 * This class provides default behaviors among server and client :
 * <br/>
 * <ul>
 *     <li>Server: started with property "port" (you can override onMasterServerXXXEvent(...) methods to do
 *     whatever process you want according to the event</li>
 *     <li>Client: added to WebSocketClientHandler in order to try to reconnect to master server in
 *     a loop delayed with the "reconnectDelay" property</li>
 *     <li>Each Kevoree Group related methods are handled in this class and you are not supposed to override
 *     them right away. AWebSocketGroup comes with a set of methods that make the difference for
 *     pull/push/update/triggerModelUpdate/etc requests between <b>master server</b> and <b>client</b>.
 *     So if you want to do some work on a <em>push request</em> which is targetting a <b>client node</b> you put
 *     it in onClientPush(ContainerRoot, String) ... etc etc</li>
 * </ul>
 *
 *
 * User: leiko
 * Date: 3/27/13
 * Time: 3:59 PM
 */
@DictionaryType({
        @DictionaryAttribute(name = "port", optional = true, fragmentDependant = true),
        @DictionaryAttribute(name = "key"),
        @DictionaryAttribute(name = "gui", defaultValue = "false", vals = {	"true", "false" }),
        // mvn_serv: tells wether or not you want a maven repo on the master node
        @DictionaryAttribute(name = "mvn_repo", defaultValue = "false", vals = {"true", "false"}),
        // indicate which port to use for the maven server
        // yes I use "puerto" instead of "port" because the checker won't let me use
        // a non-fragmentDependant port value otherwise cause the checker only speaks english
        // I'm cool with the Spanish one "puerto"
        @DictionaryAttribute(name = "repo_puerto", optional = true),
        @DictionaryAttribute(name = "reconnectDelay", defaultValue = "5000", optional = false, fragmentDependant = true)
})
@GroupType
public abstract class AWebSocketGroup extends AbstractGroupType implements DeployUnitResolver {

    //========================
    // Protocol related fields
    //========================
    protected static final byte PUSH = 1;
    protected static final byte PULL = 2;
    protected static final byte REGISTER = 3;
    protected static final byte UPDATED = 4;

    //========================
    // Client related fields
    //========================
    private static final long DEFAULT_RECONNECT_DELAY = 5000;
    private boolean isClient = false;
    protected long reconnectDelay = DEFAULT_RECONNECT_DELAY;
    protected WebSocketClient client;
    protected WebSocketClientHandler wsClientHandler;

    //========================
    // Server related fields
    //========================
    protected WebServer server;
    protected Integer port = null;
    protected Map<WebSocketConnection, String> clients;

    //========================
    // Maven related fields
    //========================
    protected boolean mvnRepo = false;
    protected int mvnRepoPort;
    protected WebServer mvnServer;
    protected AtomicReference<List<String>> remoteURLS;
    protected ExecutorService pool = null;
    protected AtomicReference<ContainerRoot> cachedModel = new AtomicReference<ContainerRoot>();

    @Start
    public void startWSGroup() throws Exception {
        // first of all : check if model is ok
        checkNoMultipleMasterServer();

        // check wether or not we want a mvnRepo with this group
        mvnRepo = Boolean.parseBoolean(getDictionary().get("mvn_repo").toString());

        // check whether this node wants to be a server or a client
        Object portVal = getDictionary().get("port");
        if (portVal != null) {
            String strPort = getDictionary().get("port").toString().trim();
            if (strPort.isEmpty()) {
                // there is no port set for this node => client
                isClient = true;

                // retrieve client-specific properties
                reconnectDelay = Long.parseLong(getDictionary().get("reconnectDelay").toString());

                // process local client start
                localStartClient();

                // dispatch client start event
                onClientStart();

                // process to do if maven is requested
                if (mvnRepo) {
                    localClientMavenStart();
                }

            } else {
                // there is a port set for this node => server
                port = Integer.parseInt(strPort);

                // process local server start
                localStartServer();

                // dispatch server start event
                onServerStart();

                // process to do if maven is requested
                if (mvnRepo) {
                    localServerMavenStart();
                }
            }
        }
    }

    @Stop
    public void stopWSGroup() {
        if (isClient) {
            localStopClient();
            onClientStop();
        } else {
            localStopServer();
            onServerStop();
        }
    }

    @Update
    public void updateWSGroup() throws Exception {
        if (isClient) {
            localClientUpdate();
            onClientUpdate();
        } else {
            localServerUpdate();
            onServerUpdate();
        }
    }

    @Override
    public boolean preUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        if (isClient) {
            localClientPreUpdate(currentModel, proposedModel);
            return onClientPreUpdate(currentModel, proposedModel);
        } else {
            localServerPreUpdate(currentModel, proposedModel);
            return onServerPreUpdate(currentModel, proposedModel);
        }
    }

    @Override
    public void push(ContainerRoot model, String targetNodeName)
            throws MultipleMasterServerException, NoMasterServerFoundException, NotAMasterServerException {
        if (targetNodeName.equals(getMasterServerNodeName())) {
            List<URI> serverURIs = getMasterServerURIs();
            onServerPush(model, targetNodeName, serverURIs);
        } else {
            onClientPush(model, targetNodeName);
        }
    }

    @Override
    public ContainerRoot pull(String targetNodeName) throws Exception {
        if (targetNodeName.equals(getMasterServerNodeName())) {
            List<URI> serverURIs = getMasterServerURIs();
            return onServerPull(targetNodeName, serverURIs);
        } else {
            return onClientPull(targetNodeName);
        }
    }

    @Override
    public void triggerModelUpdate() {
        if (isClient) {
            onClientTriggerModelUpdate();
        } else {
            onServerTriggerModelUpdate();
        }
    }

    @Override
    public File resolve(DeployUnit du) {
        File resolved = getBootStrapperService().resolveArtifact(
                du.getUnitName(), du.getGroupName(), du.getVersion(), remoteURLS.get());
        Log.info("DU " + du.getUnitName() + " from cache resolution " + (resolved != null));
        return null;
    }

    protected void updateLocalModel(final ContainerRoot model) {
        Log.debug("local update model");
        new Thread() {
            public void run() {
                try {
                    getModelService().unregisterModelListener(AWebSocketGroup.this);
                    getModelService().atomicUpdateModel(model);
                    getModelService().registerModelListener(AWebSocketGroup.this);
                } catch (Exception e) {
                    Log.error("", e);
                }

                if (isClient) {
                    onClientUpdateLocalModelDone();
                } else {
                    onServerUpdateLocalModelDone();
                }
            }
        }.start();
    }

    /**
     * Will throw a MultipleMasterServerException if there is 0 or more than 1 node
     * in this group that has its "port" property set.
     * Otherwise, nothing happens, meaning that you are good to go with that model
     *
     * @throws MultipleMasterServerException
     */
    private void checkNoMultipleMasterServer() throws MultipleMasterServerException {
        Group group = getModelElement();
        int portDefined = 0;
        for (ContainerNode subNode : group.getSubNodes()) {
            if (group != null) {
                String portOption = KevoreePropertyHelper.instance$.getProperty(
                        group, "port", true, subNode.getName());
                if (portOption != null && !portOption.trim().isEmpty()) {
                    portDefined++;
                    if (portDefined > 1) {
                        // we have more than 1 port defined in this group nodes
                        throw new MultipleMasterServerException(
                                "You are not supposed to give multiple master server with this group. " +
                                        "Just give a port to one and only one node in this group.");
                    }
                }
            }
        }
    }

    /**
     * Parse the current model to return the name of the node that
     * is the master server in this group
     *
     * @return String master server node name
     * @throws NoMasterServerFoundException if there is no master server found in the current model
     * @throws MultipleMasterServerException if there is multiple master server found in the current model
     */
    protected String getMasterServerNodeName()
            throws NoMasterServerFoundException, MultipleMasterServerException {
        Group group = getModelElement();
        ContainerRoot model = getModelService().getLastModel();

        int portDefined = 0;
        String masterServerNodeName = null;

        for (ContainerNode subNode : group.getSubNodes()) {
            Group groupOption = model.findByPath("groups[" + getName() + "]",
                    Group.class);
            if (groupOption != null) {
                String portOption = KevoreePropertyHelper.instance$.getProperty(
                        groupOption, "port", true, subNode.getName());
                if (portOption != null) {
                    if (!portOption.trim().isEmpty()) {
                        portDefined++;
                        masterServerNodeName = subNode.getName();
                    }
                }
            }
        }

        if (portDefined == 1) {
            return masterServerNodeName;

        } else if (portDefined == 0) {
            //we have no master server defined...abort
            throw new NoMasterServerFoundException("Unable to find a master server node in this group. You have to set the property \"port\" for one and only one node");
        } else {
            // we have multiple master server node defined... abort
            throw new MultipleMasterServerException(
                    "You are not supposed to give multiple master server with" +
                            " this group. Just give a port to one and only one node in this group.");
        }
    }

    /**
     * Starts a WebSocket server on the current node
     * Resets the map that keeps active client connections
     */
    private void localStartServer() {
        // this node is good to go : this is the master server
        clients = new HashMap<WebSocketConnection, String>();

        server = WebServers.createWebServer(port);
        server.add("/", handler);
        server.start();

        Log.debug("Master WebSocket server started on ws://{}:{}/", server.getUri().getHost(), server.getUri().getPort()+"");
    }

    /**
     * Creates a WebSocketClientHandler and adds a new entry to it for
     * each addresses of master server port (it will attempt to connect to each one
     * in a loop delayed with the given "reconnectDelay" property)
     */
    private void localStartClient() {
        Map<String, Integer> serverAddresses = getMasterServerAddresses();
        if (serverAddresses.isEmpty()) {
            throw new IllegalArgumentException(
                    "There is no master server node in "
                            + getNodeName()
                            + "'s web socket group so I'm kinda trapped here. "
                            + "Please specify a master server node!");
        } else {
            // create a WebSocketClientHandler that will try to connect to master server
            // in a loop delayed with "reconnectDelay" property
            wsClientHandler = new WebSocketClientHandler(reconnectDelay);

            // define the process to do when connection will be made
            wsClientHandler.setHandler(new ConnectionTask.Handler() {
                @Override
                public void onMessage(ByteBuffer bytes) {
                    Log.debug("Compressed model given by master server: loading...");
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes.array());
                    ContainerRoot model = KevoreeXmiHelper.instance$.loadCompressedStream(bais);
                    updateLocalModel(model);
                    Log.debug("Model loaded from XMI compressed bytes");
                }

                @Override
                public void onConnectionSucceeded(WebSocketClient cli) {
                    // keep a pointer on that winner
                    client = cli;
                    Log.debug("Client {} on {} connected to master server.", client.getURI().toString(), getNodeName());

                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.write(REGISTER);
                        baos.write(getNodeName().getBytes());
                        client.send(baos.toByteArray());

                    } catch (IOException e) {
                        Log.error("", e);
                    }
                }

                @Override
                public void onConnectionClosed(WebSocketClient cli) {
                    if (cli.equals(client)) {
                        client = null;
                    }
                }
            });

            // start a connection task in the WebSocketClientHandler for each IP found for master server
            for (Map.Entry<String, Integer> address : serverAddresses.entrySet()) {
                String ip = address.getKey();
                Integer port = address.getValue();
                wsClientHandler.startConnectionTask(URI.create("ws://" + ip + ":" + port + "/"));
            }
        }
    }

    private void localStopClient() {
        if (wsClientHandler != null) {
            wsClientHandler.stopAllTasks();
        }

        if (client != null) {
            client.close();
        }
    }

    private void localStopServer() {
        clients.clear();

        if (server != null) {
            server.stop();
        }
    }

    private void localClientUpdate() throws Exception {
        if (client != null) {
            URI currentURI = client.getURI();
            boolean hasChanged = true;
            for (URI uri : getMasterServerURIs()) {
                if (uri.toString().equals(currentURI.toString())) {
                    // master server did not change, no need to restart
                    hasChanged = false;
                }
            }

            if (hasChanged) {
                stopWSGroup();
                startWSGroup();
            }
        }
    }

    private void localServerUpdate() throws Exception {
        if (server != null) {
            int portProp = Integer.parseInt(getDictionary().get("port").toString());
            if (portProp != port) {
                stopWSGroup();
                startWSGroup();
            }
        }
    }

    private void localClientMavenStart() {
        mvnRepoPort = Integer.parseInt(getDictionary().get("repo_puerto").toString());
        remoteURLS = new AtomicReference<List<String>>();
        remoteURLS.set(new ArrayList<String>());
        getBootStrapperService().getKevoreeClassLoaderHandler().registerDeployUnitResolver(this);
    }

    private void localServerMavenStart() {
        pool = Executors.newSingleThreadExecutor();
        mvnServer = WebServers.createWebServer(mvnRepoPort);
        mvnServer.add(new StaticFileHandler(System.getProperty("user.home").toString() + File.separator + ".m2" + File.separator + "repository"));
        mvnServer.start();
    }

    private void localClientPreUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        if (mvnRepo) {
            // process to do when we are a client & mvn repo is running
            List<String> urls = new ArrayList<String>();
            Object mvnPort = KevoreePropertyHelper.instance$.getProperty(getModelElement(), "repo_puerto", false, null);
            for (Map.Entry<String, Integer> entry: getMasterServerAddresses().entrySet()) {
                String url = "http://" + entry.getKey() + ":" + mvnPort;
                Log.info("Add URL " + url);
                urls.add(url);
            }
            remoteURLS.set(urls);
        }
    }

    private void localServerPreUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        if (mvnRepo) {
            // process to do when we are a master server & mvn repo is running
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    ContainerRoot model = cachedModel.get();
                    if (model != null) {
                        for (DeployUnit du : model.getDeployUnits()) {
                            Log.debug("CacheFile for DU : " + du.getUnitName() + ":" + du.getGroupName() + ":" + du.getVersion());
                            File cachedFile = getBootStrapperService().resolveDeployUnit(du);
                        }
                    }
                }
            });
        }
    }

    /**
     * Returns a map a ip -> port retrieved from master server node.
     * If no ip was given, then 127.0.0.1 will be added to the returned map.
     * @return Map<String, Integer> a map of ("ip", port) for the master server node
     */
    protected Map<String, Integer> getMasterServerAddresses() {
        Map<String, Integer> map = new HashMap<String, Integer>();

        Group group = getModelElement();
        ContainerRoot model = getModelService().getLastModel();
        for (ContainerNode subNode : group.getSubNodes()) {
            if (group != null) {
                String portOption = KevoreePropertyHelper.instance$.getProperty(
                        group, "port", true, subNode.getName());
                // if a port is defined then it is a master server
                if (portOption != null) {
                    int port = Integer.parseInt(portOption);
                    List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(model, subNode.getName(),
                            org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
                    if (ips.isEmpty()) {
                        // no IP defined for master server, let's give it a try locally
                        map.put("127.0.0.1", port);
                    } else {
                        for (String ip : ips) {
                            // for each ip in node network properties add an entry
                            // to the map
                            map.put(ip, port);
                        }
                    }
                }
            }
        }

        return map;
    }

    /**
     * Returns a list of URIs defined by master server node properties
     * @return List<URI>
     */
    protected List<URI> getMasterServerURIs() {
        List<URI> serverURIs = new ArrayList<URI>();
        StringBuilder scheme;

        for (Map.Entry<String, Integer> address : getMasterServerAddresses().entrySet()) {
            scheme = new StringBuilder();
            scheme.append("ws://").append(address.getKey()).append(":").append(address.getValue());
            serverURIs.add(URI.create(scheme.toString()));
        }
        return serverURIs;
    }

    /**
     * Tells whether or not one has auth on this group
     * If the "gui" property is set to "true" then the "key" will be asked
     * with a GUI otherwise it will check the one given in "key" property
     *
     * @return true if property "key" is good; otherwise false
     */
    protected boolean checkAuth() {
        String key = getDictionary().get("key").toString();
        boolean usingGUI = Boolean.parseBoolean(getDictionary().get("gui")
                .toString());

        if (usingGUI) {
            // using GUI file chooser to provide a key file
            JFileChooser jfc = new JFileChooser(new File("."));
            if (jfc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                if (KeyChecker.validate(jfc.getSelectedFile())) {
                    return true;
                } else {
                    JOptionPane
                            .showMessageDialog(
                                    null,
                                    "I cannot authenticate you with this key. Aborting...",
                                    "Wrong keyfile selected",
                                    JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
        // else using key directly provided in dictionnary
        return KeyChecker.validate(key);
    }

    //========================================
    // Client's context group-related methods
    //========================================
    protected void onClientStart() {}
    protected void onClientStop() {}
    protected void onClientUpdate() {}
    protected boolean onClientPreUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        return true;
    }
    protected void onClientUpdateLocalModelDone() {}
    protected abstract void onClientPush(ContainerRoot model, String targetNodeName)
            throws MultipleMasterServerException, NoMasterServerFoundException, NotAMasterServerException;
    protected abstract ContainerRoot onClientPull(String targetNodeName) throws Exception;
    protected abstract void onClientTriggerModelUpdate();

    //========================================
    // Server's context group-related methods
    //========================================
    protected void onServerStart() {}
    protected void onServerStop() {}
    protected void onServerUpdate() {}
    protected boolean onServerPreUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        return true;
    }
    protected void onServerUpdateLocalModelDone() {}
    protected void onMasterServerPushEvent(WebSocketConnection conn, byte[] msg) {}
    protected void onMasterServerPullEvent(WebSocketConnection conn, byte[] msg) {}
    protected void onMasterServerRegisterEvent(WebSocketConnection conn, String nodeToRegister) {}
    protected void onMasterServerUpdatedEvent(WebSocketConnection conn) {}
    protected void onMasterServerOpenEvent(WebSocketConnection conn) {}
    protected void onMasterServerCloseEvent(WebSocketConnection conn) {}
    protected abstract void onServerPush(ContainerRoot model, String masterServerNodeName, List<URI> addresses)
            throws MultipleMasterServerException, NoMasterServerFoundException;
    protected abstract ContainerRoot onServerPull(String masterServerNodeName, List<URI> addresses)
            throws Exception;
    protected abstract void onServerTriggerModelUpdate();

    //========================================
    // Handlers
    //========================================
    private BaseWebSocketHandler handler = new BaseWebSocketHandler() {
        @Override
        public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
            // get rid of the control byte for contextual methods
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(msg, 1, msg.length-1);
            byte[] cleanMsg = baos.toByteArray();

            // trigger proper event according to control byte
            switch (msg[0]) {
                case PUSH:
                    onMasterServerPushEvent(connection, cleanMsg);
                    break;

                case PULL:
                    onMasterServerPullEvent(connection, cleanMsg);
                    break;

                case REGISTER:
                    onMasterServerRegisterEvent(connection, new String(cleanMsg));
                    break;

                case UPDATED:
                    onMasterServerUpdatedEvent(connection);
                    break;

                default:
                    Log.debug("Receiving {} as msg[0]: I do NOT know this control byte! Known control bytes are {}",msg[0]+"", new Byte[]{PUSH, PULL, REGISTER, UPDATED}+"");
                    break;
            }
        }

        public void onOpen(WebSocketConnection connection) throws Exception {
            onMasterServerOpenEvent(connection);
        }

        @Override
        public void onClose(WebSocketConnection connection) throws Exception {
            onMasterServerCloseEvent(connection);
        }
    };
}
