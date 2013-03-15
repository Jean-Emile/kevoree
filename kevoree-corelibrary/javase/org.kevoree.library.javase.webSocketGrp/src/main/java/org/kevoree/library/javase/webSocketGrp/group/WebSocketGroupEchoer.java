package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.GroupType;
import org.kevoree.framework.KevoreeXmiHelper;


/**
 * This WebSocketGroup do the exact same work as WebSocketGroupMasterServer but it adds
 * more flexibility for push requests.
 * With this group you are not forced to push a model on the master server node. Each node can
 * process the request: if the node is just a client it will forward the push request to the master server
 * and then get the result as an echo back to update its own model.
 * 
 * @author Leiko
 *
 */
@GroupType
public class WebSocketGroupEchoer extends WebSocketGroupMasterServer {
	
	@Override
	public void push(ContainerRoot model, String targetNodeName)
			throws Exception {
		// we do not check if targeted node is a master server
		// serialize model into an OutputStream
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		KevoreeXmiHelper.$instance.saveCompressedStream(baos, model);
		byte[] data = new byte[baos.size()+1];
		byte[] serializedModel = baos.toByteArray();
		data[0] = PUSH;
		for (int i=1; i<data.length; i++) {
			data[i] = serializedModel[i-1];
		}
		
		requestPush(data);
	}
	
	@Override
	public ContainerRoot pull(final String targetNodeName) throws Exception {
		final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();
		
		Map<String, Integer> serverEntries = getMasterServerEntries();
		for (Entry<String, Integer> entry: serverEntries.entrySet()) {
			URI uri = URI.create("ws://" + entry.getKey() + ":" + entry.getValue() + "/");
	        WebSocketClient client = new WebSocketClient(uri) {
	        	@Override
	        	public void onMessage(ByteBuffer bytes) {
					logger.debug("Receiving compressed model...");
					ByteArrayInputStream bais = new ByteArrayInputStream(bytes.array());
					final ContainerRoot root = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
					try {
						exchanger.exchange(root);
					} catch (InterruptedException e) {
						logger.error("error while waiting model from " + targetNodeName, e);
					} finally {
						close();
					}
	        	}
				@Override
				public void onMessage(String msg) {}
	        	@Override
				public void onOpen(ServerHandshake arg0) {}
				@Override
				public void onError(Exception arg0) {}
				@Override
				public void onClose(int arg0, String arg1, boolean arg2) {}
			};
			client.connectBlocking();
			client.send(new byte[] {PULL});
		}
		
		// waiting for exchanger to exchange received value
		return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
	}
}
