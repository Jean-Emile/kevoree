package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Library;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.annotation.Update;
import org.kevoree.api.service.core.handler.ModelListener;
import org.kevoree.framework.AbstractGroupType;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.NodeNetworkHelper;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
import org.kevoree.library.javase.webSocketGrp.exception.NoMasterServerFoundException;
import org.kevoree.library.javase.webSocketGrp.exception.NotAMasterServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import scala.Option;

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
@DictionaryType({ @DictionaryAttribute(name = "port", optional = true, fragmentDependant = true) })
@GroupType
@Library(name = "JavaSE", names = "Android")
public class WebSocketGroupMasterServer extends AbstractGroupType {

	protected static final byte PUSH = 1;
	protected static final byte PULL = 2;
	protected static final byte REGISTER = 3;

	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	private WebServer server;
	private Map<WebSocketConnection, String> clients;
	private Integer masterPort = null;

	@Start
	public void start() throws MultipleMasterServerException {
		Object portVal = getDictionary().get("port");
		if (portVal != null) {
			masterPort = Integer.parseInt(portVal.toString().trim());
		}

		if (masterPort != null) {
			// dictionary key "port" is defined so
			// it means that this node wants to be a master server
			// well, check if there is no other node that handles the job
			Group group = getModelElement();
			ContainerRoot model = getModelService().getLastModel();
			for (ContainerNode subNode : group.getSubNodes()) {
				Group groupOption = model.findByPath("groups[" + getName()
						+ "]", Group.class);
				if (groupOption != null) {
					Option<String> portOption = KevoreePropertyHelper
							.getProperty(groupOption, "port", true,
									subNode.getName());
					if (portOption.isDefined()
							&& !subNode.getName().equals(getNodeName())) {
						// this node is not "me" and has already a defined
						// port.. sounds like we have multiple master server
						throw new MultipleMasterServerException(
								"You are not supposed to give multiple master server with this group. Just give a port to one and only one node in this group.");
					}
				}
			}

			// this node is good to go : this is the master server
			clients = new HashMap<WebSocketConnection, String>();
			server = WebServers.createWebServer(masterPort);
			server.add("/", handler);
			server.start();

			logger.debug("Master WebSocket server started on "
					+ server.getUri().toString());
		}

		getModelService().registerModelListener(modelListener);
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

					@Override
					public void onMessage(String msg) {
					}

					@Override
					public void onOpen(ServerHandshake arg0) {
					}

					@Override
					public void onError(Exception arg0) {
					}

					@Override
					public void onClose(int arg0, String arg1, boolean arg2) {
					}
				};
				client.connectBlocking();
				client.send(new byte[] { PULL });
			}
		} else {
			throw new NotAMasterServerException(
					"Pull request can only be made on master server node.");
		}

		// waiting for exchanger to exchange received value
		return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
	}

	protected String getMasterServerNodeName()
			throws NoMasterServerFoundException {
		Group group = getModelElement();
		ContainerRoot model = getModelService().getLastModel();
		for (ContainerNode subNode : group.getSubNodes()) {
			Group groupOption = model.findByPath("groups[" + getName() + "]",
					Group.class);
			if (groupOption != null) {
				Option<String> portOption = KevoreePropertyHelper.getProperty(
						groupOption, "port", true, subNode.getName());
				if (portOption.isDefined())
					return subNode.getName();
			}
		}
		throw new NoMasterServerFoundException(
				"Unable to find a master server node in this group.");
	}

	@Override
	public void push(ContainerRoot model, String targetNodeName)
			throws Exception {
		if (targetNodeName.equals(getMasterServerNodeName())) {
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
			throw new NotAMasterServerException(
					"Push request can only be made on master server node.");
		}
	}

	private void pushModel(byte[] data, String ip, int port) throws Exception {
		logger.debug("Sending model via webSocket client to master server "
				+ "ws://" + ip + ":" + port + "/");
		URI uri = URI.create("ws://" + ip + ":" + port + "/");
		WebSocketClient client = new WebSocketClient(uri) {
			@Override
			public void onMessage(String msg) {
			}

			@Override
			public void onOpen(ServerHandshake arg0) {
			}

			@Override
			public void onError(Exception arg0) {
			}

			@Override
			public void onClose(int arg0, String arg1, boolean arg2) {
			}
		};
		client.connectBlocking();
		client.send(data);
		client.close();
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

	@Override
	public void triggerModelUpdate() {

	}

	@Stop
	public void stop() {
		if (server != null) {
			server.stop();
			server = null;
			masterPort = null;
		}
	}

	@Update
	public void update() throws MultipleMasterServerException {
		stop();
		start();
	}

	protected void updateLocalModel(final ContainerRoot model) {
		logger.debug("local update model");
		new Thread() {
			public void run() {
				getModelService().unregisterModelListener(
						WebSocketGroupMasterServer.this);
				getModelService().atomicUpdateModel(model);
				getModelService().registerModelListener(
						WebSocketGroupMasterServer.this);
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
				if (portOption.isDefined()) {
					int port = Integer.parseInt(portOption.get());
					List<String> ips = KevoreePropertyHelper
							.getNetworkProperties(model, subNode.getName(),
									org.kevoree.framework.Constants
											.KEVOREE_PLATFORM_REMOTE_NODE_IP());
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

	private ModelListener modelListener = new ModelListener() {

		@Override
		public boolean preUpdate(ContainerRoot currentModel,
				ContainerRoot proposedModel) {
			return true;
		}

		@Override
		public void preRollback(ContainerRoot currentModel,
				ContainerRoot proposedModel) {
		}

		@Override
		public void postRollback(ContainerRoot currentModel,
				ContainerRoot proposedModel) {
		}

		@Override
		public void modelUpdated() {
			if (masterPort == null) {
				// this node is just a client
				Map<String, Integer> serverEntries = getMasterServerEntries();
				if (serverEntries.isEmpty()) {
					throw new IllegalArgumentException(
							"There is no master server node in "
									+ getNodeName()
									+ "'s web socket group so I'm kinda trapped here. "
									+ "Please specify a master server node!");

				} else {
					for (Entry<String, Integer> entry : serverEntries
							.entrySet()) {
						String ip = entry.getKey();
						Integer port = entry.getValue();
						try {
							// we found the master server, give URI to WebSocket
							// client
							URI uri = URI.create("ws://" + ip + ":" + port
									+ "/");
							WebSocketClient client = new WebSocketClient(uri) {
								@Override
								public void onMessage(ByteBuffer bytes) {
									logger.debug("Compressed model given by master server: loading...");
									ByteArrayInputStream bais = new ByteArrayInputStream(
											bytes.array());
									ContainerRoot model = KevoreeXmiHelper.$instance
											.loadCompressedStream(bais);
									updateLocalModel(model);
									logger.debug("Model loaded from XMI compressed bytes");
								}

								@Override
								public void onMessage(String msg) {
								}

								@Override
								public void onOpen(ServerHandshake arg0) {
								}

								@Override
								public void onError(Exception arg0) {
								}

								@Override
								public void onClose(int arg0, String arg1,
										boolean arg2) {
								}
							};
							try {
								client.connectBlocking();
								byte[] serializedNodeName = getNodeName()
										.getBytes();
								byte[] data = new byte[serializedNodeName.length + 1];
								data[0] = REGISTER;
								for (int i = 0; i < serializedNodeName.length; i++) {
									data[i + 1] = serializedNodeName[i];
								}
								client.send(data);
							} catch (InterruptedException e) {
								logger.error(
										"Unable to connect to master server", e);
							}
						} catch (Exception e) {
							logger.error(
									"Unable to register client on master server "
											+ ip
											+ ":"
											+ port
											+ ", maybe it's not the right ip:port",
									e);
						}
					}
				}
			}
		}

		@Override
		public boolean initUpdate(ContainerRoot currentModel,
				ContainerRoot proposedModel) {
			return true;
		}

		@Override
		public boolean afterLocalUpdate(ContainerRoot currentModel,
				ContainerRoot proposedModel) {
			return true;
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
		logger.debug("PUSH: " + connection.httpRequest().remoteAddress()
				+ " asked for a PUSH");
		ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1,
				msg.length - 1);
		ContainerRoot model = KevoreeXmiHelper.$instance
				.loadCompressedStream(bais);
		updateLocalModel(model);

		logger.debug("server knows: " + clients.toString());
		// broadcasting model to each client
		for (WebSocketConnection conn : clients.keySet()) {
			logger.debug("Trying to push model to client "
					+ conn.httpRequest().remoteAddress());
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
		logger.debug("REGISTER: New client added to active connections: "
				+ connection.httpRequest().remoteAddress());
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
	 * associated with the CLOSE event requested from another client.
	 * 
	 * @param connection
	 *            a client
	 */
	protected void onMasterServerCloseEvent(WebSocketConnection connection) {
		clients.remove(connection);
		logger.debug("CLOSE: Client "
				+ connection.httpRequest().remoteAddress()
				+ " closed connection with server. Removed from active connections.");
	}

	protected Map<WebSocketConnection, String> getClients() {
		return this.clients;
	}
}
