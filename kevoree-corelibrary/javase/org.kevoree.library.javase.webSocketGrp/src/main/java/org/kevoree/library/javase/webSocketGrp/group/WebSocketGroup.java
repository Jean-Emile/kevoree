package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
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
import org.kevoree.framework.AbstractGroupType;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.NodeNetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import scala.Option;


/**
 * WebSocketGroup that launches a server & a client on each fragment
 * 
 * @author Leiko
 *
 */
@DictionaryType({
		@DictionaryAttribute(name = "port", defaultValue = "8000", optional = true, fragmentDependant = true)})
@GroupType
@Library(name = "JavaSE", names = "Android")
public class WebSocketGroup extends AbstractGroupType {

	private static final String PUSH_RES = "/push";
	private static final String PULL_RES = "/pull";
	private static final String _ZIP = "/zip";

	protected Logger logger = LoggerFactory.getLogger(WebSocketGroup.class);

	private WebServer server;
	private int port;
	private boolean isStarted = false;

	@Start
	public void startWebSocketGroup() {
		port = Integer.parseInt(getDictionary().get("port").toString());

		server = WebServers.createWebServer(port);
		server.add(PUSH_RES, pushHandler);
		server.add(PUSH_RES + _ZIP, pushCompressedHandler);
		server.add(PUSH_RES, pullHandler);
		server.add(PULL_RES + _ZIP, pullCompressedHandler);

		startServer();
		logger.debug("WebSocket server started on port " + port);
	}

	@Stop
	public void stopWebSocketGroup() {
		stopServer();
	}

	@Update
	public void updateRestGroup() throws IOException {
		logger.debug("updateRestGroup");
		if (port != Integer.parseInt(this.getDictionary().get("port")
				.toString())) {
			stopWebSocketGroup();
			startWebSocketGroup();
		}
	}

	@Override
	public ContainerRoot pull(final String targetNodeName) throws Exception {
		logger.debug("pull(" + targetNodeName + ")");

		int PORT = 8000;
		ContainerRoot model = getModelService().getLastModel();
		Group groupOption = model.findByPath("groups[" + getName() + "]",
				Group.class);
		if (groupOption != null) {
			Option<String> portOption = KevoreePropertyHelper.getProperty(
					groupOption, "port", true, targetNodeName);
			if (portOption.isDefined()) {
				try {
					PORT = Integer.parseInt(portOption.get());
				} catch (NumberFormatException e) {
					logger.warn(
							"Attribute \"port\" of {} must be an Integer. Default value ({}) is used",
							getName(), PORT);
				}
			}
		}

		List<String> ips = KevoreePropertyHelper.getNetworkProperties(model,
				targetNodeName, org.kevoree.framework.Constants
						.KEVOREE_PLATFORM_REMOTE_NODE_IP());
		if (ips.size() > 0) {
			for (String ip : ips) {
				try {
					return requestModel(ip, PORT, targetNodeName);
				} catch (Exception e) {
					logger.debug("Unable to request model on {} using {}",
							targetNodeName, ip + ":" + PORT, e);
				}
			}
		} else {
			try {
				return requestModel("127.0.0.1", PORT, targetNodeName);
			} catch (Exception e) {
				logger.debug("Unable to request model on {} using {}",
						targetNodeName, "127.0.0.1:" + PORT, e);
			}
		}
		throw new Exception("Unable to pull model on " + targetNodeName);
	}

	private ContainerRoot requestModel(String ip, int port,
			final String nodeName) throws Exception {
		logger.debug("Trying to pull model from " + "ws://" + ip + ":" + port
				+ PULL_RES + _ZIP);
		final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();
		WebSocketClient client = new WebSocketClient(URI.create("ws://" + ip
				+ ":" + port + PULL_RES + _ZIP)) {
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
					logger.error("error while waiting model from " + nodeName,
							e);
				} finally {
					close();
				}
			}

			@Override
			public void onMessage(String msg) {
				logger.debug("Receiving model...");
				final ContainerRoot root = KevoreeXmiHelper.$instance
						.loadString(msg);
				try {
					exchanger.exchange(root);
				} catch (InterruptedException e) {
					logger.error("error while waiting model from " + nodeName,
							e);
				} finally {
					close();
				}
			}

			@Override
			public void onOpen(ServerHandshake sh) {
			}

			@Override
			public void onError(Exception e) {
				close();
				try {
					exchanger.exchange(null);
				} catch (InterruptedException ex) {
					logger.error("", ex);
				}
			}

			@Override
			public void onClose(int code, String reason, boolean flag) {
			}
		};
		client.connectBlocking();
		client.send("gimme model"); // freaking useless message, it's just to
									// make the server respond
		return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void triggerModelUpdate() {
		logger.debug("trigger model update");
		if (isStarted) {
			final ContainerRoot modelOption = NodeNetworkHelper.updateModelWithNetworkProperty(this);
			if (modelOption != null) {
				try {
					updateLocalModel(modelOption);
				} catch (Exception e) {
					logger.error("", e);
				}
			}
			isStarted = false;
		} else {
			Group group = getModelElement();
			ContainerRoot currentModel = (ContainerRoot) group.eContainer();
			for (ContainerNode subNode : group.getSubNodes()) {
				if (!subNode.getName().equals(this.getNodeName())) {
					try {
						logger.debug("trigger model update by pushing to " + subNode.getName());
						push(currentModel, subNode.getName());
					} catch (Exception e) {
						logger.warn("Unable to notify other members of {} group", group.getName());
					}
				}
			}
		}
	}

	@Override
	public void push(ContainerRoot model, String targetNodeName)
			throws Exception {
		logger.debug("trying to push");

		int PORT = 8000;
		Group groupOption = model.findByPath("groups[" + getName() + "]",
				Group.class);
		if (groupOption != null) {
			Option<String> portOption = KevoreePropertyHelper.getProperty(
					groupOption, "port", true, targetNodeName);
			if (portOption.isDefined()) {
				try {
					PORT = Integer.parseInt(portOption.get());
				} catch (NumberFormatException e) {
					logger.warn(
							"Attribute \"port\" of {} must be an Integer. Default value ({}) is used",
							getName(), PORT);
				}
			}
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		KevoreeXmiHelper.$instance.saveCompressedStream(output, model);
		byte[] data = output.toByteArray();

		List<String> ips = KevoreePropertyHelper.getNetworkProperties(model,
				targetNodeName, org.kevoree.framework.Constants
						.KEVOREE_PLATFORM_REMOTE_NODE_IP());
		if (ips.size() > 0) {
			for (String ip : ips) {
				try {
					pushModel(data, ip, PORT);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.warn("No addr, found default local");
			try {
				pushModel(data, "127.0.0.1", PORT);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void pushModel(byte[] data, String ip, int port) throws Exception {
		logger.debug("Trying to push model to " + "ws://" + ip + ":" + port
				+ PUSH_RES + _ZIP);
		WebSocketClient client = new WebSocketClient(URI.create("ws://" + ip
				+ ":" + port + PUSH_RES + _ZIP)) {
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

	protected void updateLocalModel(final ContainerRoot model) {
		logger.debug("local update model");
		new Thread() {
			public void run() {
				getModelService().unregisterModelListener(WebSocketGroup.this);
				getModelService().atomicUpdateModel(model);
				getModelService().registerModelListener(WebSocketGroup.this);
			}
		}.start();
	}

	private void startServer() {
		if (server != null)
			server.start();
		isStarted = true;
	}

	private void stopServer() {
		if (server != null)
			server.stop();
		isStarted = false;
	}

	private BaseWebSocketHandler pushHandler = new BaseWebSocketHandler() {
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
			logger.debug("Model received from "
					+ connection.httpRequest().header("Host") + ": loading...");
			ByteArrayInputStream bais = new ByteArrayInputStream(msg);
			ContainerRoot model = KevoreeXmiHelper.$instance.loadStream(bais);
			updateLocalModel(model);
			logger.debug("Model loaded from XMI String");
		}
	};

	private BaseWebSocketHandler pullHandler = new BaseWebSocketHandler() {
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
			logger.debug("Pull request received from "
					+ connection.httpRequest().header("Host") + ": loading...");
			String stringifiedModel = KevoreeXmiHelper.$instance.saveToString(
					getModelService().getLastModel(), false);
			connection.send(stringifiedModel);
			logger.debug("Model pulled back to "
					+ connection.httpRequest().header("Host"));
		}
	};

	private BaseWebSocketHandler pushCompressedHandler = new BaseWebSocketHandler() {
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
			logger.debug("Compressed model received from "
					+ connection.httpRequest().header("Host") + ": loading...");
			ByteArrayInputStream bais = new ByteArrayInputStream(msg);
			ContainerRoot model = KevoreeXmiHelper.$instance
					.loadCompressedStream(bais);
			updateLocalModel(model);
			logger.debug("Model loaded from XMI String");
		}
	};

	private BaseWebSocketHandler pullCompressedHandler = new BaseWebSocketHandler() {
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
			logger.debug("Pull request received from "
					+ connection.httpRequest().header("Host") + ": loading...");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			KevoreeXmiHelper.$instance.saveCompressedStream(output,
					getModelService().getLastModel());
			connection.send(output.toByteArray());
			logger.debug("Compressed model pulled back to "
					+ connection.httpRequest().header("Host"));
		}

		public void onMessage(WebSocketConnection connection, String msg)
				throws Throwable {
			onMessage(connection, new byte[] {});
		}
	};
}
