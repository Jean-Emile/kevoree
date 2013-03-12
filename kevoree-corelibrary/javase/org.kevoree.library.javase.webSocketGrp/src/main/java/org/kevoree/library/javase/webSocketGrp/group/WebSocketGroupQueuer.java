package org.kevoree.library.javase.webSocketGrp.group;

import org.kevoree.annotation.GroupType;

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

}
