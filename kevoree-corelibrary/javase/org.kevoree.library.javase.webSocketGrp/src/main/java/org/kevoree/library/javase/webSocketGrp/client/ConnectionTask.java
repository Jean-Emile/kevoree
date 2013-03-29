package org.kevoree.library.javase.webSocketGrp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private URI uri;
    private Handler handler;

    public ConnectionTask(URI uri, Handler handler) {
        this.uri = uri;
        this.handler = handler;
    }

    @Override
    public void run() {
        logger.debug("[START] ConnectionTask: trying to connect to {} ...", uri);
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
                logger.debug("Unable to connect to {}", uri);
            }
        } catch (InterruptedException e) {
            logger.debug("[STOP] ConnectionTask to {} = interrupted", uri);
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
