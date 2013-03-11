package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
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
import org.kevoree.framework.NetworkHelper;
import org.kevoree.library.NodeNetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import scala.Option;

@DictionaryType({
		@DictionaryAttribute(name = "port", defaultValue = "8000", optional = true, fragmentDependant = true)})
@GroupType
@Library(name = "JavaSE", names = "Android")
public class WebSocketGroup extends AbstractGroupType {

	private static final String PUSH_RES = "/push";
	private static final String PULL_RES = "/pull";
	private static final String _ZIP = "/zip";
	private static final byte PULL = 0;
	
	protected Logger logger = LoggerFactory.getLogger(WebSocketGroup.class);
	
	private WebServer server;
	private WebSocketClientFactory factory;
	private int port;
	private boolean isStarted = false;

	@Start
	public void startWebSocketGroup() {
		port = Integer.parseInt(getDictionary().get("port").toString());

		server = WebServers.createWebServer(port);
		server.add(PUSH_RES, pushHandler);
		server.add(PUSH_RES+_ZIP, pushCompressedHandler);
		server.add(PUSH_RES, pullHandler);
		server.add(PULL_RES+_ZIP, pullCompressedHandler);
		
		startServer();
		logger.debug("WebSocket server started on port "+port);
	}

	@Stop
	public void stopWebSocketGroup() {
		stopServer();
	}
	
    @Update
    public void updateRestGroup() throws IOException {
    	logger.debug("updateRestGroup");
        if (port != Integer.parseInt(this.getDictionary().get("port").toString())) {
            stopWebSocketGroup();
            startWebSocketGroup();
        }
    }

	@Override
	public ContainerRoot pull(final String targetNodeName) throws Exception {
		logger.debug("pull("+targetNodeName+")");
		
        ContainerRoot model = getModelService().getLastModel();
        String ip = "127.0.0.1";
        List<String> ips = KevoreePropertyHelper.getNetworkProperties(model, targetNodeName, org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
        if (ipOption.isDefined()) {
            ip = ipOption.get();
        }
        int PORT = 8000;
        Group groupOption = model.findByPath("groups[" + getName() + "]", Group.class);
        if (groupOption != null) {
            Option<String> portOption = KevoreePropertyHelper.getProperty(groupOption, "port", true, targetNodeName);
            if (portOption.isDefined()) {
                try {
                    PORT = Integer.parseInt(portOption.get());
                } catch (NumberFormatException e){
                    logger.warn("Attribute \"port\" of {} must be an Integer. Default value ({}) is used", getName(), PORT);
                }
            }
        }
        
        logger.debug("Trying to pull model from "+"ws://"+ip+":"+PORT+PULL_RES+_ZIP);
        final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();
        URI uri = URI.create("ws://"+ip+":"+PORT+PULL_RES+_ZIP);
        WebSocketClient client = factory.newWebSocketClient();
        WebSocket.Connection conn = client.open(uri, new WebSocket.OnBinaryMessage() {
        	
			@Override
			public void onMessage(byte[] data, int offset, int length) {
				logger.debug("Receiving compressed model...");
				ByteArrayInputStream bais = new ByteArrayInputStream(data);
				final ContainerRoot root = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
				try {
					exchanger.exchange(root);
				} catch (InterruptedException e) {
					logger.error("error while waiting model from " + targetNodeName, e);
				} finally {
					// TODO close
				}
			}
        	
			@Override
			public void onOpen(Connection connection) {}
			@Override
			public void onClose(int closeCode, String message) {}

		}, 5, TimeUnit.SECONDS);

        byte[] data = new byte[] {PULL};
        conn.sendMessage(data, 0, data.length);
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
                    	logger.debug("trigger model update by pushing to "+subNode.getName());
                        push(currentModel, subNode.getName());
                    } catch (Exception e) {
                        logger.warn("Unable to notify other members of {} group", group.getName());
                    }
                }
            }
        }
	}

	@Override
	public void push(ContainerRoot model, String targetNodeName) throws Exception {
		logger.debug("trying to push");
		
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KevoreeXmiHelper.$instance.saveCompressedStream(output, model);
        output.flush();
        String ip = "127.0.0.1";
        Option<String> ipOption = NetworkHelper.getAccessibleIP(KevoreePropertyHelper.getNetworkProperties(model, targetNodeName, org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP()));
        if (ipOption.isDefined()) {
            ip = ipOption.get();
        } else {
            logger.warn("No addr, found default local");
        }

        int PORT = 8000;
        Group groupOption = model.findByPath("groups[" + getName() + "]", Group.class);
        if (groupOption!=null) {
            Option<String> portOption = KevoreePropertyHelper.getProperty(groupOption, "port", true, targetNodeName);
            if (portOption.isDefined()) {
                try {
                PORT = Integer.parseInt(portOption.get());
                } catch (NumberFormatException e){
                    logger.warn("Attribute \"port\" of {} must be an Integer. Default value ({}) is used", getName(), PORT);
                }
            }
        }
        logger.debug("Trying to push model to "+"ws://"+ip+":"+PORT+PUSH_RES+_ZIP);
        WebSocketClient client = factory.newWebSocketClient();
        URI uri = URI.create("ws://"+ip+":"+PORT+PUSH_RES+_ZIP);
        WebSocket.Connection conn = client.open(uri, new WebSocket() {
			@Override
			public void onOpen(Connection connection) {}
			@Override
			public void onClose(int closeCode, String message) {}
		}, 5, TimeUnit.SECONDS);
        
        byte[] data = output.toByteArray();
 		conn.sendMessage(data, 0, data.length);
		conn.close();
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
    	if (server != null) server.start();
    	isStarted = true;
    }
    
    private void stopServer() {
    	if (server != null) server.stop();
    	isStarted = false;
    }
    
    private BaseWebSocketHandler pushHandler = new BaseWebSocketHandler() {
    	public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
			logger.debug("Model received from "+connection.httpRequest().header("Host")+": loading...");
			ByteArrayInputStream bais = new ByteArrayInputStream(msg);
			ContainerRoot model = KevoreeXmiHelper.$instance.loadStream(bais);
			updateLocalModel(model);
			logger.debug("Model loaded from XMI String");
    	}
    };
    
    private BaseWebSocketHandler pullHandler = new BaseWebSocketHandler() {
    	public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
			logger.debug("Pull request received from "+connection.httpRequest().header("Host")+": loading...");
			String stringifiedModel = KevoreeXmiHelper.$instance.saveToString(getModelService().getLastModel(), false);
			connection.send(stringifiedModel);
			logger.debug("Model pulled back to "+connection.httpRequest().header("Host"));
    	}
    };
    
    private BaseWebSocketHandler pushCompressedHandler = new BaseWebSocketHandler() {
    	public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
			logger.debug("Compressed model received from "+connection.httpRequest().header("Host")+": loading...");
			ByteArrayInputStream bais = new ByteArrayInputStream(msg);
			ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
			updateLocalModel(model);
			logger.debug("Model loaded from XMI String");
    	}
    };
    
    private BaseWebSocketHandler pullCompressedHandler = new BaseWebSocketHandler() {
    	public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
			logger.debug("Pull request received from "+connection.httpRequest().header("Host")+": loading...");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
            KevoreeXmiHelper.$instance.saveCompressedStream(output, getModelService().getLastModel());
			connection.send(output.toByteArray());
			logger.debug("Compressed model pulled back to "+connection.httpRequest().header("Host"));
    	}
    	
    	public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
			onMessage(connection, new byte[] {});
    	}
    };
}
