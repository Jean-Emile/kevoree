package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.GroupType;
import org.kevoree.framework.KevoreeXmiHelper;
import org.webbitserver.WebSocketConnection;

/**
 * This WebSocketGroup do the exact same work as WebSocketGroupEchoer but
 * it adds a handler to queue the push requests to the nodes that have not
 * already established a connection to the master server.
 * 
 * @author Leiko
 * 
 */
@GroupType
public class WebSocketGroupQueuer extends WebSocketGroupEchoer {

	@Override
	protected void onMasterServerPushEvent(WebSocketConnection connection,
			byte[] msg) {
		super.onMasterServerPushEvent(connection, msg);
		// TODO TODO TODO TODO
//		logger.debug("PUSH: "+connection.httpRequest().remoteAddress()+" asked for a PUSH");
//		ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length-1);
//		ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
//		updateLocalModel(model);
//		
//		logger.debug("server knows: "+getClients().toString());
//		// broadcasting model to each client
//		for (WebSocketConnection conn : getClients()) {
//			logger.debug("Trying to push model to client "+conn.httpRequest().remoteAddress());
//			conn.send(msg, 1, msg.length-1); // offset is for the control byte
//		}
	}
}
