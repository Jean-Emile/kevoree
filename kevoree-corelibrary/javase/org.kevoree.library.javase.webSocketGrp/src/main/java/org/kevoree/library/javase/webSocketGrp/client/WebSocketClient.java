package org.kevoree.library.javase.webSocketGrp.client;

import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/19/13
 * Time: 1:48 PM
 */
public class WebSocketClient extends org.java_websocket.client.WebSocketClient {

    public WebSocketClient(URI uri) {
        super(uri);
    }

	@Override
	public void onError(Exception msg) {}

	@Override
	public void onMessage(String msg) {}
	
	@Override
	public void onMessage(ByteBuffer bytes) {}

	@Override
	public void onOpen(ServerHandshake serverHandshake) {}

    @Override
    public void onClose(int code, String reason, boolean flag) {}
}
