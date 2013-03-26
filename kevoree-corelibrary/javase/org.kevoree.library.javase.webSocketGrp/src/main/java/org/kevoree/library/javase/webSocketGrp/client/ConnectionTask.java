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
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionTask extends Thread {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private URI uri;
    private Handler handler;
    private long loopTime;
    private boolean run = true;

    public ConnectionTask(URI uri, long loopTime, Handler handler) {
        this.uri = uri;
        this.handler = handler;
        this.loopTime = loopTime;
    }

    @Override
    public void run() {
        while (run) {
            try {
                WebSocketClient client = new WebSocketClient(uri) {
                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        handler.onMessage(bytes);
                    }
                };
                boolean connSucceeded = client.connectBlocking();
                if (connSucceeded) {
                    handler.onConnectionSucceed(client);
                    return;
                }
            } catch (InterruptedException e) {
                logger.warn("", e);
            } finally {
                logger.debug("Unable to connect to {}, new attempt in {}ms", uri, loopTime);
                // take a nap
                try { Thread.sleep(loopTime); }
                catch (InterruptedException e1) {/* one does not simply care */}
            }
        }
        handler.onStop();
        logger.debug("ConnectionTask on {} stopped", uri);
    }

    public void kill() {
        this.run = false;
    }

    // ===============
    //    HANDLER
    // ===============
    public interface Handler {
        void onMessage(ByteBuffer bytes);
        void onConnectionSucceed(WebSocketClient client);
        void onStop();
    }
}
