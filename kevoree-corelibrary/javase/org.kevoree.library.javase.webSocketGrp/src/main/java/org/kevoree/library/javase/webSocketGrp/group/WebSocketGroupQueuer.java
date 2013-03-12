package org.kevoree.library.javase.webSocketGrp.group;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.annotation.GroupType;
import org.kevoree.annotation.Start;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.javase.webSocketGrp.exception.MultipleMasterServerException;
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
	
	private Map<String, ContainerRoot> waitingQueue;
	
	@Override
	@Start
	public void start() throws MultipleMasterServerException {
		super.start();
		
		waitingQueue = new HashMap<String, ContainerRoot>();
	}
	
	@Override
	protected void onMasterServerPushEvent(WebSocketConnection connection,
			byte[] msg) {
		super.onMasterServerPushEvent(connection, msg);
		
		Group group = getModelElement();
		ContainerRoot model = getModelService().getLastModel();
		
		// for each node in this group
		for (ContainerNode subNode : group.getSubNodes()) {
			if (!subNode.getName().equals(getNodeName())) {
				// this node is not "me" check if we already have an active connection with him or not
				if (containsNode(subNode.getName())) {
					// we already have an active connection with this client
					// so lets send the model back
					connection.send(msg, 1, msg.length - 1); // offset is for the control byte
					
				} else {
					// we do not have an active connection with this client
					// meaning that we have to store the model and wait for
					// him to connect in order to send the model back
					waitingQueue.put(subNode.getName(), model);
					logger.debug(subNode.getName()+" is not yet connected to master server. It has been added to waiting queue.");
				}
			}
		}
	}
	
	@Override
	protected void onMasterServerRegisterEvent(WebSocketConnection connection,
			String nodeName) {
		super.onMasterServerRegisterEvent(connection, nodeName);
		if (waitingQueue.containsKey(nodeName)) {
			// if we ends up here, it means that this node wasn't connected
			// when a push request was initiated earlier and though it has
			// to get the new model back
			logger.debug(nodeName+" is in the waiting queue, meaning that we have to send the model back to him");
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			KevoreeXmiHelper.$instance.saveStream(baos, waitingQueue.get(nodeName));
			connection.send(baos.toByteArray());
			waitingQueue.remove(nodeName);
		}
	}
	
	private boolean containsNode(String nodeName) {
		for (String name : getClients().values()) {
			if (nodeName.equals(name)) return true;
		}
		return false;
	}
}
