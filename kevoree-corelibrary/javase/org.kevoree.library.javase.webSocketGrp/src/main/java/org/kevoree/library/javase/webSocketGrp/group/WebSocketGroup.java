package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractGroupType;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.NodeNetworkHelper;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.log.Log;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;


/**
 * WebSocketGroup that launches a server on each node and initiate a client
 * connection for each push/pull request
 * 
 * @author Leiko
 *
 */
@DictionaryType({
		@DictionaryAttribute(name = "port", defaultValue = "8000", optional = true, fragmentDependant = true)})
@GroupType
@Library(name = "JavaSE", names = "Android")
public class WebSocketGroup extends AbstractGroupType {

    //========================
    // Protocol related fields
    //========================
    protected static final byte PUSH = 1;
    protected static final byte PULL = 2;
	private WebServer server;
	private int port;
	private boolean isStarted = false;

	@Start
	public void startWebSocketGroup() {
		port = Integer.parseInt(getDictionary().get("port").toString());

		server = WebServers.createWebServer(port);
		server.add("/", serverHandler);

		startServer();
		Log.debug("WebSocket server started on port " + port);
	}

	@Stop
	public void stopWebSocketGroup() {
		stopServer();
	}

	@Update
	public void updateRestGroup() throws IOException {
		Log.debug("updateRestGroup");
		if (port != Integer.parseInt(this.getDictionary().get("port")
				.toString())) {
			stopWebSocketGroup();
			startWebSocketGroup();
		}
	}

	@Override
	public ContainerRoot pull(final String targetNodeName) throws Exception {
		Log.debug("pull(" + targetNodeName + ")");

		int PORT = 8000;
		ContainerRoot model = getModelService().getLastModel();
		Group groupOption = model.findByPath("groups[" + getName() + "]",
				Group.class);
		if (groupOption != null) {
			String portOption = KevoreePropertyHelper.instance$.getProperty(
					groupOption, "port", true, targetNodeName);
			if (portOption != null) {
				try {
					PORT = Integer.parseInt(portOption);
				} catch (NumberFormatException e) {
					Log.warn(
							"Attribute \"port\" of {} must be an Integer. Default value ({}) is used",
							getName(), PORT+"");
				}
			}
		}

		List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(model,
				targetNodeName, org.kevoree.framework.Constants.instance$
						.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
		if (ips.size() > 0) {
			for (String ip : ips) {
				try {
					return requestModel(ip, PORT, targetNodeName);
				} catch (Exception e) {
					Log.debug("Unable to request model on {} using {}",e,
							targetNodeName, ip + ":" + PORT+"");
				}
			}
		} else {
			try {
				return requestModel("127.0.0.1", PORT, targetNodeName);
			} catch (Exception e) {
				Log.debug("Unable to request model on {} using {}",e,
						targetNodeName, "127.0.0.1:" + PORT+"");
			}
		}
		throw new Exception("Unable to pull model on " + targetNodeName);
	}

	private ContainerRoot requestModel(String ip, int port,
			final String nodeName) throws Exception {
		Log.debug("Trying to pull model from " + "ws://" + ip + ":" + port);
		final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();
		WebSocketClient client = new WebSocketClient(URI.create("ws://" + ip + ":" + port)) {
			@Override
			public void onMessage(ByteBuffer bytes) {
				Log.debug("Receiving compressed model...");
				ByteArrayInputStream bais = new ByteArrayInputStream(
						bytes.array());
				final ContainerRoot root = KevoreeXmiHelper.instance$
						.loadCompressedStream(bais);
				try {
					exchanger.exchange(root);
				} catch (InterruptedException e) {
					Log.error("error while waiting model from " + nodeName,
							e);
				} finally {
					close();
				}
			}

			@Override
			public void onMessage(String msg) {
				Log.debug("Receiving model...");
				final ContainerRoot root = KevoreeXmiHelper.instance$
						.loadString(msg);
				try {
					exchanger.exchange(root);
				} catch (InterruptedException e) {
					Log.error("error while waiting model from " + nodeName,
							e);
				} finally {
					close();
				}
			}

			@Override
			public void onError(Exception e) {
				close();
				try {
					exchanger.exchange(null);
				} catch (InterruptedException ex) {
					Log.error("", ex);
				}
			}
		};
		if (client.connectBlocking()) {
            client.send(new byte[] {PULL});
        }

		return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void triggerModelUpdate() {
		Log.debug("trigger model update");
		if (isStarted) {
			final ContainerRoot modelOption = NodeNetworkHelper.updateModelWithNetworkProperty(this);
			if (modelOption != null) {
				try {
					updateLocalModel(modelOption);
				} catch (Exception e) {
					Log.error("", e);
				}
			}
			isStarted = false;
		} else {
			Group group = getModelElement();
			ContainerRoot currentModel = (ContainerRoot) group.eContainer();
			for (ContainerNode subNode : group.getSubNodes()) {
				if (!subNode.getName().equals(this.getNodeName())) {
					try {
						Log.debug("trigger model update by pushing to " + subNode.getName());
						push(currentModel, subNode.getName());
					} catch (Exception e) {
						Log.warn("Unable to notify other members of {} group", group.getName());
					}
				}
			}
		}
	}

	@Override
	public void push(ContainerRoot model, String targetNodeName)
			throws Exception {
		int PORT = 8000;
		Group groupOption = model.findByPath("groups[" + getName() + "]",
				Group.class);
		if (groupOption != null) {
			String portOption = KevoreePropertyHelper.instance$.getProperty(
					groupOption, "port", true, targetNodeName);
			if (portOption != null) {
				try {
					PORT = Integer.parseInt(portOption);
				} catch (NumberFormatException e) {
					Log.warn(
							"Attribute \"port\" of {} must be an Integer. Default value ({}) is used",
							getName(), PORT+"");
				}
			}
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		KevoreeXmiHelper.instance$.saveCompressedStream(output, model);
		byte[] data = output.toByteArray();

		List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(model,
				targetNodeName, org.kevoree.framework.Constants.instance$
						.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
		if (ips.size() > 0) {
			for (String ip : ips) {
				try {
					pushModel(data, ip, PORT);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			Log.warn("No addr, found default local");
			try {
				pushModel(data, "127.0.0.1", PORT);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void pushModel(byte[] data, String ip, int port) throws Exception {
		Log.debug("Trying to push model to " + "ws://" + ip + ":" + port);
		WebSocketClient client = new WebSocketClient(URI.create("ws://" + ip + ":" + port));
		if (client.connectBlocking()) {
            client.send(data);
            client.close();
        }
	}

	protected void updateLocalModel(final ContainerRoot model) {
		Log.debug("local update model");
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

	private BaseWebSocketHandler serverHandler = new BaseWebSocketHandler() {
		public void onMessage(WebSocketConnection connection, byte[] msg)
				throws Throwable {
            switch (msg[0]) {
                case PUSH:
                    Log.debug("Compressed model received from "
                            + connection.httpRequest().header("Host") + ": loading...");
                    ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length-1);
                    ContainerRoot model = KevoreeXmiHelper.instance$
                            .loadCompressedStream(bais);
                    updateLocalModel(model);
                    Log.debug("Model loaded from XMI String");
                    break;

                case PULL:
                    Log.debug("Pull request received from "
                            + connection.httpRequest().header("Host") + ": loading...");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    KevoreeXmiHelper.instance$.saveCompressedStream(output, getModelService().getLastModel());
                    connection.send(output.toByteArray());
                    Log.debug("Compressed model pulled back to "
                            + connection.httpRequest().header("Host"));
                    break;

                default:
                    break;
            }
		}
	};
}
