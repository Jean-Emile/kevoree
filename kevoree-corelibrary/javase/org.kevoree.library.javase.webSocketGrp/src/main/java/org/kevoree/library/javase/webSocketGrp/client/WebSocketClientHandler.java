package org.kevoree.library.javase.webSocketGrp.client;

import org.kevoree.log.Log;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/26/13
 * Time: 9:36 AM
 * To change this template use File | Settings | File Templates.
 */
public class WebSocketClientHandler {

    private static final long DEFAULT_LOOP_TIME = 5000;
    private long loopTime;
    private ConnectionTask.Handler handler;
    private ScheduledExecutorService pool;

    public WebSocketClientHandler(long loopTime) {
        this.loopTime = loopTime;
        this.handler = defaultHandler;
        this.pool = Executors.newScheduledThreadPool(10);
    }

    public WebSocketClientHandler(ConnectionTask.Handler handler) {
        this(DEFAULT_LOOP_TIME);
        this.handler = handler;
    }

    public WebSocketClientHandler() {
        this(DEFAULT_LOOP_TIME);
    }

    public void setHandler(ConnectionTask.Handler handler) {
        this.handler = handler;
    }

    public void startConnectionTask(URI uri) {
        pool.scheduleWithFixedDelay(new ConnectionTask(uri, handler), 0, loopTime, TimeUnit.MILLISECONDS);
    }

    public void stopAllTasks() {
        this.pool.shutdownNow();
    }

    private ConnectionTask.Handler defaultHandler = new ConnectionTask.Handler() {
        @Override
        public void onMessage(ByteBuffer bytes) {
            Log.debug("DefaultHandler: onMessage(ByteBuffer) called.");
        }

        @Override
        public void onConnectionSucceeded(WebSocketClient client) {
            Log.debug("DefaultHandler: onConnectionSucceed(WebSocketClient) called.");
        }

        @Override
        public void onConnectionClosed(WebSocketClient client) {
            Log.debug("DefaultHandler: onConnectionClosed(WebSocketClient) called.");
        }
    };
}
