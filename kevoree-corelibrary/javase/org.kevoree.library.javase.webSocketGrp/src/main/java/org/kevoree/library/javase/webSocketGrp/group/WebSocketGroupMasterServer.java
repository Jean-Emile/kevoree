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
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.library.javase.webSocketGrp.dummy.KeyChecker;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.library.javase.webSocketGrp.exception.NoMasterServerFoundException;
import org.kevoree.library.javase.webSocketGrp.exception.NotAMasterServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.*;
import org.webbitserver.handler.StaticFileHandler;
import scala.Option;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocketGroup that launches a server on the node fragment if and only if a
 * port is given in the node fragment dictionary attribute. This group requires
 * one and only one node to act as a server (so with a given port in this
 * group's attributes) You can only trigger PUSH events on the master server or
 * the event will be lost into the wild. Once you've pushed a model on the
 * master server, this node fragment will handle model broadcasting on each
 * connected node (so if a node hasn't established a connection to this server
 * he will not get notified)
 * 
 * @author Leiko
 * 
 */
@DictionaryType({
		@DictionaryAttribute(name = "port", optional = true, fragmentDependant = true),
		@DictionaryAttribute(name = "key"),
		@DictionaryAttribute(name = "gui", defaultValue = "false", vals = {	"true", "false" }),
        // mvn_serv: tells wether or not you want a maven repo on the master node
        @DictionaryAttribute(name = "mvn_repo", defaultValue = "false", vals = {"true", "false"}),
        // indicate which port to use for the maven server
        // yes I use "puertos" instead of "port" because the checker won't let me use
        // a non-fragmentDependant port value otherwise cause the checker only speaks english
        // I'm cool with the Spanish one "puerto"
        @DictionaryAttribute(name = "repo_puerto", optional = true)
})
@Library(name = "JavaSE", names = "Android")
@GroupType
public class WebSocketGroupMasterServer extends AbstractGroupType implements DeployUnitResolver {

    private static final int DEFAULT_MVN_PORT = 8042;

	protected static final byte PUSH = 1;
	protected static final byte PULL = 2;
	protected static final byte REGISTER = 3;
	protected static final byte UPDATED = 4;

	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	private WebServer server;
	private WebSocketClient client;
	private Map<WebSocketConnection, String> clients;
	private Integer port = null;
	private int updatedClientCounter = 0;
	private long startPushBroadcastTime;
    private boolean mvnRepo = false;
    private int mvnRepoPort = DEFAULT_MVN_PORT;
    private ExecutorService pool = null;
    private AtomicReference<ContainerRoot> cachedModel = new AtomicReference<ContainerRoot>();
    private WebServer mvnServer;
    private AtomicReference<List<String>> remoteURLS;

	@Start
	public void start() throws MultipleMasterServerException {
		logger.debug("START");

		Object portVal = getDictionary().get("port");
		if (portVal != null) {
			port = Integer.parseInt(portVal.toString().trim());
		}

		if (port != null) {
			// dictionary key "port" is defined so
			// it means that this node wants to be a master server
			// well, check if there is no other node that handles the job
			checkNoMultipleMasterServer();

			// this node is good to go : this is the master server
			clients = new HashMap<WebSocketConnection, String>();
			
			startServer();

            mvnRepo = Boolean.parseBoolean(getDictionary().get("mvn_repo").toString());
            if (mvnRepo) {
                try {
                    mvnRepoPort = Integer.parseInt(getDictionary().get("repo_puerto").toString());
                } catch (NumberFormatException e) {
                    logger.warn("Unable to parse \"repo_port\" from dictionary. Using default value {} instead.", DEFAULT_MVN_PORT);
                }

                pool = Executors.newSingleThreadExecutor();
                mvnServer = WebServers.createWebServer(mvnRepoPort);
                mvnServer.add(new StaticFileHandler(System.getProperty("user.home").toString() + File.separator + ".m2" + File.separator + "repository"));
                mvnServer.start();
            } else {
                remoteURLS = new AtomicReference<List<String>>();
                remoteURLS.set(new ArrayList<String>());
                getBootStrapperService().getKevoreeClassLoaderHandler().registerDeployUnitResolver(this);
            }


		} else {
			// this node is just a client
			Map<String, Integer> serverEntries = getMasterServerEntries();
			if (serverEntries.isEmpty()) {
				throw new IllegalArgumentException(
						"There is no master server node in "
								+ getNodeName()
								+ "'s web socket group so I'm kinda trapped here. "
								+ "Please specify a master server node!");

			} else {
				for (Entry<String, Integer> entry : serverEntries.entrySet()) {
					String ip = entry.getKey();
					Integer port = entry.getValue();
					try {
						// we found the master server, give URI to WebSocket
						// client
						URI uri = URI.create("ws://" + ip + ":" + port + "/");
						client = new WebSocketClient(uri) {
							@Override
							public void onMessage(ByteBuffer bytes) {
								logger.debug("Compressed model given by master server: loading...");
								ByteArrayInputStream bais = new ByteArrayInputStream(bytes.array());
								ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
								updateLocalModel(model);
								logger.debug("Model loaded from XMI compressed bytes");
							}
						};
						client.connectBlocking();
						logger.debug(
								"Client {} on {} connected to master server.",
								client.getURI(), getNodeName());
						byte[] serializedNodeName = getNodeName().getBytes();
						byte[] data = new byte[serializedNodeName.length + 1];
						data[0] = REGISTER;
						for (int i = 0; i < serializedNodeName.length; i++) {
							data[i + 1] = serializedNodeName[i];
						}
						client.send(data);

					} catch (InterruptedException e) {
						logger.error("Unable to connect to master server", e);

					} catch (Exception e) {
						logger.error(
								"Unable to register client on master server "
										+ ip + ":" + port
										+ ", maybe it's not the right ip:port",
								e);
					}
				}
			}
		}
	}

	private void checkNoMultipleMasterServer()
			throws MultipleMasterServerException {
		Group group = getModelElement();
		ContainerRoot model = getModelService().getLastModel();
		int portDefined = 0;
		for (ContainerNode subNode : group.getSubNodes()) {
			Group groupOption = model.findByPath("groups[" + getName() + "]",
					Group.class);
			if (groupOption != null) {
				Option<String> portOption = KevoreePropertyHelper.getProperty(
						groupOption, "port", true, subNode.getName());
				if (portOption.isDefined() && !portOption.get().trim().isEmpty()) {
					portDefined++;
					if (portDefined > 1) {
						// we have more than 1 port defined in this group nodes
						throw new MultipleMasterServerException(
								"You are not supposed to give multiple master server with this group. Just give a port to one and only one node in this group.");
					}
				}
			}
		}
	}

	@Override
	public ContainerRoot pull(final String targetNodeName) throws Exception {
		final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();

		if (targetNodeName.equals(getMasterServerNodeName())) {
			Map<String, Integer> serverEntries = getMasterServerEntries();
			for (Entry<String, Integer> entry : serverEntries.entrySet()) {
				URI uri = URI.create("ws://" + entry.getKey() + ":"
						+ entry.getValue() + "/");
				WebSocketClient client = new WebSocketClient(uri) {
					@Override
					public void onMessage(ByteBuffer bytes) {
						logger.debug("Receiving compressed model...");
						ByteArrayInputStream bais = new ByteArrayInputStream(
								bytes.array());
						final ContainerRoot root = KevoreeXmiHelper.$instance
								.loadCompressedStream(bais);
						try {
							exchanger.exchange(root);
						} catch (InterruptedException e) {
							logger.error("error while waiting model from "
									+ targetNodeName, e);
						} finally {
							close();
						}
					}
				};
				client.connectBlocking();
				client.send(new byte[] { PULL });
			}
		} else {
			throw new NotAMasterServerException("Pull request can only be made on master server node.");
		}

		// waiting for exchanger to exchange received value
		return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
	}

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
				Option<String> portOption = KevoreePropertyHelper.getProperty(
						groupOption, "port", true, subNode.getName());
				if (portOption.isDefined()) {
					if (!portOption.get().trim().isEmpty()) {
						portDefined++;
						masterServerNodeName = subNode.getName();
					}
				}
			}
		}
		if (portDefined > 1) {
			// we have multiple master server node defined... abort
			throw new MultipleMasterServerException(
					"You are not supposed to give multiple master server with" +
					" this group. Just give a port to one and only one node in this group.");
		} else if (portDefined == 0) {
			//we have no master server defined...abort
			throw new NoMasterServerFoundException("Unable to find a master server node in this group.");
		} else {
			// we have the name of the master server node
			return masterServerNodeName;
		}
	}

	private void startServer() {
		server = WebServers.createWebServer(port);
		server.add("/", handler);
		server.start();

		logger.debug("Master WebSocket server started on ws://{}:{}/", server.getUri().getHost(), server.getUri().getPort());
	}

	private void stopServer() {
		if (server != null) {
			server.stop();
			server = null;
			port = null;
		}
	}

	@Override
	public void push(ContainerRoot model, String targetNodeName) throws Exception {
		if (targetNodeName.equals(getMasterServerNodeName())) {
			// check user authorization
			if (checkAuth()) {
				// user is authenticated
				// serialize model into an OutputStream
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				KevoreeXmiHelper.$instance.saveCompressedStream(baos, model);
				byte[] data = new byte[baos.size() + 1];
				byte[] serializedModel = baos.toByteArray();
				data[0] = PUSH;
				for (int i = 1; i < data.length; i++) {
					data[i] = serializedModel[i - 1];
				}

				requestPush(data);

			} else {
				// user is not authenticated
				throw new IllegalAccessError("You do not have the right to push a model");
			}
		} else {
			throw new NotAMasterServerException(
					"Push request can only be made on master server node.");
		}
	}

	private void pushModel(byte[] data, String ip, int port) throws Exception {
		logger.debug("Sending model via webSocket client to master server "
				+ "ws://" + ip + ":" + port + "/");
		URI uri = URI.create("ws://" + ip + ":" + port + "/");
		WebSocketClient client = new WebSocketClient(uri);
		try {
			client.connectBlocking();
			client.send(data);

		} catch (Exception e) {
			logger.error("Connection to server impossible", e);
		} finally {
            if (client != null && client.getConnection().isOpen()) {
                try { client.close(); } catch (Exception ex) {}
            }
        }
	}

	protected void requestPush(byte[] data) {
		Map<String, Integer> serverEntries = getMasterServerEntries();
		for (Entry<String, Integer> entry : serverEntries.entrySet()) {
			String ip = entry.getKey();
			Integer port = entry.getValue();
			try {
				pushModel(data, ip, port);
			} catch (Exception e) {
				logger.error("Unable to push model to " + ip + ":" + port);
			}
		}
	}

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
									"This key does not give you the right to push. Aborting...",
									"Wrong keyfile selected",
									JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}
		}
		// else using key directly provided in dictionnary
		return KeyChecker.validate(key);
	}

	@Override
	public void triggerModelUpdate() {
        logger.debug("triggerModelUpdate");
        if (client != null) {
            final ContainerRoot modelOption = org.kevoree.library.NodeNetworkHelper.updateModelWithNetworkProperty(this);
            if (modelOption != null) updateLocalModel(modelOption);
            client = null;
        } else {
            // serialize model into an OutputStream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            KevoreeXmiHelper.$instance.saveCompressedStream(baos, getModelService().getLastModel());
            byte[] data = new byte[baos.size() + 1];
            byte[] serializedModel = baos.toByteArray();
            data[0] = PUSH;
            for (int i = 1; i < data.length; i++) {
                data[i] = serializedModel[i - 1];
            }

            requestPush(data);
        }
	}

	@Stop
	public void stop() {
		if (client != null) {
			// we are a client and we want to stop
            if (!client.getConnection().isClosed() || !client.getConnection().isClosing()) {
                try {
                    client.closeBlocking();
                    client = null;
                    logger.debug("Client on {} closed connection with master server", getNodeName());
                } catch (InterruptedException e) {
                    logger.warn("Client ({}) on {}: closing connection failed",
                            getNodeName(), client.getURI(), e);
                }
            }
		} else {
			// we are a server
			stopServer();
		}
	}

    @Override
    public boolean preUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        cachedModel.set(proposedModel);
        if (mvnRepo) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    ContainerRoot model = cachedModel.get();
                    if (model != null) {
                        for (DeployUnit du : model.getDeployUnits()) {
                            logger.debug("CacheFile for DU : " + du.getUnitName() + ":" + du.getGroupName() + ":" + du.getVersion());
                            File cachedFile = getBootStrapperService().resolveDeployUnit(du);
                        }
                    }
                }
            });
        } else {
            List<String> urls = new ArrayList<String>();
            ContainerRoot model = getModelService().getLastModel();
            for (Group group : model.getGroups()) {
                if (group.getTypeDefinition().getName().equals(WebSocketGroupMasterServer.class.getSimpleName())) {
                    for (ContainerNode child : group.getSubNodes()) {
                        Object server = KevoreePropertyHelper.getProperty(group, "mvn_repo", true, child.getName());
                        if (server != null) {
                            logger.info("Cache Found on node " + child.getName());
                            List<String> ips = KevoreePropertyHelper.getNetworkProperties(model, child.getName(), org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
                            Object port = KevoreePropertyHelper.getProperty(child, "repo_puerto", false, null);
                            for (String remoteIP : ips) {
                                String url = "http://" + remoteIP + ":" + port;
                                logger.info("Add URL " + url);
                                urls.add(url);
                            }
                        }
                    }
                }
            }
            remoteURLS.set(urls);
        }
        return true;
    }

	@Update
	public void update() throws MultipleMasterServerException {
		logger.debug("UPDATE");
		checkNoMultipleMasterServer();

		if (port != null) {
			// it means that we are a master server
			int newPort = Integer.parseInt(getDictionary().get("port").toString());
			if (newPort != port) {
				logger.debug("Dictionary master server port has changed, stopping old server and restarting a new one...");
				// 1_ stop current server
				stopServer();

				// we need to restart the server on the new port
				port = newPort;

				// 2_ restart server on new port
				startServer();
			} else {
				logger.debug("No update needed, server has kept the same state as before");
			}

		} else {
			logger.debug("Updating client...");
			// i'm just a client lets go stop/start me
			// by doing so I'm gonna be registered on the right server for sure
			stop();
			start();
		}

        if (mvnRepo) {
            // we were a mvn repo too
            // now check if we still are one
            if (Boolean.parseBoolean(getDictionary().get("mvn_repo").toString())) {
                // yes we still are one
                // do we need to restart the server ?
                if (mvnRepoPort != Integer.parseInt(getDictionary().get("repo_puerto").toString())) {
                    try {
                        mvnRepoPort = Integer.parseInt(getDictionary().get("repo_puerto").toString());
                    } catch (NumberFormatException e) {
                        logger.warn("Unable to parse \"repo_port\" from dictionary. Using default value {} instead.", DEFAULT_MVN_PORT);
                    }

                    mvnServer = WebServers.createWebServer(mvnRepoPort);
                    mvnServer.add(new StaticFileHandler(System.getProperty("user.home").toString() + File.separator + ".m2" + File.separator + "repository"));
                    mvnServer.start();
                    logger.debug("Maven repo server started on {}", mvnServer.getUri());
                }
            } else {
                // no we do not want to be a maven repo anymore
                // stoping maven server
                mvnServer.stop();
                mvnRepo = false;
            }
        }
	}

	protected void updateLocalModel(final ContainerRoot model) {
		logger.debug("local update model");
		new Thread() {
			public void run() {
				try {
					getModelService().unregisterModelListener(WebSocketGroupMasterServer.this);
					getModelService().atomicUpdateModel(model);
					getModelService().registerModelListener(WebSocketGroupMasterServer.this);
				} catch (Exception e) {
					logger.error("", e);
				}

				// if we are a client notify server that we are done updating local model
				if (client != null && client.getConnection().isOpen()) {
					try {
						client.send(new byte[] { UPDATED });
					} catch (Exception e) {
						logger.error("Trying to send UPDATED control byte to server with a closed client", e);
					}
				}
			}
		}.start();
	}

	protected Map<String, Integer> getMasterServerEntries() {
		Map<String, Integer> map = new HashMap<String, Integer>();

		Group group = getModelElement();
		ContainerRoot model = getModelService().getLastModel();
		for (ContainerNode subNode : group.getSubNodes()) {
			Group groupOption = model.findByPath("groups[" + getName() + "]",
					Group.class);
			if (groupOption != null) {
				Option<String> portOption = KevoreePropertyHelper.getProperty(
						groupOption, "port", true, subNode.getName());
				// if a port is defined then it is a master server
				if (portOption.isDefined() && !portOption.get().trim().isEmpty()) {
					int port = Integer.parseInt(portOption.get());
					List<String> ips = KevoreePropertyHelper.getNetworkProperties(model, subNode.getName(),
									org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
					for (String ip : ips) {
						// for each ip in node network properties add an entry
						// to the map
						map.put(ip, port);
					}
				}
			}
		}

		return map;
	}

	private BaseWebSocketHandler handler = new BaseWebSocketHandler() {
		@Override
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
			switch (msg[0]) {
			case PUSH:
				onMasterServerPushEvent(connection, msg);
				break;

			case PULL:
				onMasterServerPullEvent(connection, msg);
				break;

			case REGISTER:
				onMasterServerRegisterEvent(connection, new String(msg, 1,
						msg.length - 1));
				break;

			case UPDATED:
				onMasterServerUpdatedEvent(connection);
				break;

			default:
				logger.debug("Receiving "
						+ msg[0]
						+ " as byte[0]: do NOT know this message. Known bytes are "
						+ PULL + ", " + PUSH);
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

	/**
	 * In this context you are a master server and you should do the work
	 * associated with the PUSH event requested from another client.
	 * 
	 * @param connection
	 *            a client
	 * @param msg
	 */
	protected void onMasterServerPushEvent(WebSocketConnection connection,
			byte[] msg) {
		logger.debug("PUSH: " + connection.httpRequest().remoteAddress() + " asked for a PUSH");
		ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length - 1);
		ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
		updateLocalModel(model);

		startPushBroadcastTime = System.currentTimeMillis();

		logger.debug("Master websocket server is going to broadcast model over {} clients", clients.size());
		// broadcasting model to each client
		for (WebSocketConnection conn : clients.keySet()) {
			logger.debug("Trying to push model to client " + conn.httpRequest().remoteAddress());
			conn.send(msg, 1, msg.length - 1); // offset is for the control byte
		}
	}

	/**
	 * In this context you are a master server and you should do the work
	 * associated with the PULL event requested from another client.
	 * 
	 * @param connection
	 *            a client
	 * @param msg
	 */
	protected void onMasterServerPullEvent(WebSocketConnection connection,
			byte[] msg) {
		logger.debug("PULL: Client " + connection.httpRequest().remoteAddress()
				+ " ask for a pull");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		KevoreeXmiHelper.$instance.saveCompressedStream(output,
				getModelService().getLastModel());
		connection.send(output.toByteArray());
	}

	protected void onMasterServerUpdatedEvent(WebSocketConnection connection) {
		updatedClientCounter++;
		if (updatedClientCounter == clients.size()) {
			// every clients did their local model update
			logger.debug("The " + clients.size()
					+ " connected clients made their local update in "
					+ (System.currentTimeMillis() - startPushBroadcastTime)
					+ "ms");
			updatedClientCounter = 0;
		}
	}

	/**
	 * In this context you are a master server and you should do the work
	 * associated with the REGISTER event requested from another client.
	 * 
	 * @param connection
	 *            a client
	 * @param nodeName
	 *            the node that initiated the registration
	 */

	protected void onMasterServerRegisterEvent(WebSocketConnection connection,
			String nodeName) {
		clients.put(connection, nodeName);
		logger.debug(
				"REGISTER: New client ({}) added to active connections: {}",
				nodeName, connection.httpRequest().remoteAddress());
		// sending master server nodeName back to client
		connection.send(getNodeName());
	}

	/**
	 * In this context you are a master server and you should do the work
	 * associated with the OPEN event requested from another client.
	 * 
	 * @param connection
	 *            a client
	 */
	protected void onMasterServerOpenEvent(WebSocketConnection connection) {
		logger.debug("OPEN: New client opens connection: "
				+ connection.httpRequest().remoteAddress());
	}

	/**
	 * In this context you are a master server and you should do the work
	 * associated with the CLOSE event triggered by a client.
	 * 
	 * @param connection
	 *            a client
	 */
	protected void onMasterServerCloseEvent(WebSocketConnection connection) {
		String str = "";
		String nodeName;
		if ((nodeName = clients.remove(connection)) != null) {
			str = " => "+nodeName + " removed from active connections.";
		}
		logger.debug("CLOSE: Client "
				+ connection.httpRequest().remoteAddress()
				+ " closed connection with server" + str);
	}

	protected Map<WebSocketConnection, String> getClients() {
		return this.clients;
	}

    @Override
    public File resolve(DeployUnit du) {
        File resolved = getBootStrapperService().resolveArtifact(du.getUnitName(), du.getGroupName(), du.getVersion(), remoteURLS.get());
        logger.info("DU " + du.getUnitName() + " from cache resolution " + (resolved != null));
        return null;
    }
}
