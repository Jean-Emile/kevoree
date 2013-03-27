package org.kevoree.library.javase.webSocketGrp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/26/13
 * Time: 9:36 AM
 * To change this template use File | Settings | File Templates.
 */
public class WebSocketClientHandler {

    private static final long DEFAULT_LOOP_TIME = 5000;

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private long loopTime;
    private ConnectionTask.Handler handler;
    private Map<URI, ConnectionTask> tasks;

    public WebSocketClientHandler(long loopTime) {
        this.tasks = new HashMap<URI, ConnectionTask>();
        this.loopTime = loopTime;
        this.handler = defaultHandler;
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

    private void addConnectionTask(final URI uri) {
        ConnectionTask task = new ConnectionTask(uri, loopTime, handler);
        tasks.put(uri, task);
    }

    public void startConnectionTask(URI uri) {
        addConnectionTask(uri);
        tasks.get(uri).start();
    }

    public boolean stopTask(URI uri) {
        boolean res;

        if (tasks.containsKey(uri)) {
            tasks.get(uri).kill();
            tasks.remove(uri);
            res = true;
        } else {
            res = false;
        }

        return res;
    }

    public void stopAllTasks() {
        for (URI uri : tasks.keySet()) {
            stopTask(uri);
        }
    }

    private ConnectionTask.Handler defaultHandler = new ConnectionTask.Handler() {
        @Override
        public void onMessage(ByteBuffer bytes) {
            logger.debug("DefaultHandler: onMessage(ByteBuffer) called.");
        }

        @Override
        public void onConnectionSucceeded(WebSocketClient client) {
            logger.debug("DefaultHandler: onConnectionSucceed(WebSocketClient) called.");
        }

        @Override
        public void onKilled() {
            logger.debug("DefaultHandler: onStop() called.");
        }
    };
}
