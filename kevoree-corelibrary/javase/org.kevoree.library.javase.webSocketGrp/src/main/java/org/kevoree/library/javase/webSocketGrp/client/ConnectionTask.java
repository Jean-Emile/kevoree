package org.kevoree.library.javase.webSocketGrp.client;

import org.kevoree.log.Log;

import java.net.URI;
import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/26/13
 * Time: 9:56 AM
 *
 */
public class ConnectionTask implements Runnable {

    private URI uri;
    private Handler handler;

    public ConnectionTask(URI uri, Handler handler) {
        this.uri = uri;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onMessage(ByteBuffer bytes) {
                    handler.onMessage(bytes);
                }

                @Override
                public void onClose(int code, String reason, boolean flag) {
                    handler.onConnectionClosed(this);
                }
            };

            boolean connSucceeded = client.connectBlocking();

            if (connSucceeded) {
                handler.onConnectionSucceeded(client);
                return;
            } else {
                Log.debug("Unable to connect to {}", uri.toString());
            }
        } catch (InterruptedException e) {
            Log.debug("[STOP] ConnectionTask to {} = interrupted", uri.toString());
        }
    }

    // ===============
    //    HANDLER
    // ===============
    public interface Handler {
        void onMessage(ByteBuffer bytes);
        void onConnectionSucceeded(WebSocketClient client);
        void onConnectionClosed(WebSocketClient client);
    }
}
