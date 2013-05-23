package org.kevoree.library.javase.webSocketGrp.channel;

import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.log.Log;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/21/13
 * Time: 5:36 PM
 */
public class MessageQueuer {

    private final Map<String, WebSocketClient> clients;
    private Deque<MessageHolder> queue;
    private int maxQueued;
    private boolean doStop = false;

    public MessageQueuer(int maxQueued, final Map<String, WebSocketClient> clients) {
        this.maxQueued = maxQueued;
        this.clients = clients;
        this.queue = new ArrayDeque<MessageHolder>(maxQueued);

        // create the thread that will handle message sending
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!doStop) {
                    // try to send message to the last added item in queue
                    final MessageHolder msg = queue.pollFirst();
                    if (msg != null) {
                        boolean sent = false;
                        String strURI = null;

                        for (URI uri : msg.getURIs()) {
                            WebSocketClient client = new WebSocketClient(uri) {
                                @Override
                                public void onClose(int code, String reason, boolean flag) {
                                    clients.remove(msg.getNodeName());
                                }
                            };
                            try {
                                if (client.connectBlocking()) {
                                    client.send(msg.getData());
                                    clients.put(msg.getNodeName(), client);

                                    strURI = uri.toString();
                                    sent = true;
                                }

                            } catch (InterruptedException e) {
                                // connection failed, we will try it later
                                Log.debug("Unable to connect to {}, message still in queue", uri.toString());
                            }
                        }

                        if (sent) {
                            Log.debug("Message from queue delivered to {}", strURI);
                        } else {
                            // put that message back in the queue
                            queue.addFirst(msg);
                        }
                    }

                    try {
                        // take a break dude
                        Thread.sleep(100);
                    } catch (Exception e) {/* no one cares */}
                }
                queue.clear();
                clients.clear();
            }
        }).start();
    }

    public void addToQueue(MessageHolder msg) {
        try {
            queue.addFirst(msg);
        } catch (IllegalStateException e) {
            // if we end up here the queue is full
            // so get rid of the oldest message
            queue.pollLast();
            // and add the new one
            queue.addFirst(msg);
        }
    }

    public void updateMessages(MessageUpdater syncCallback) {
        synchronized (queue) {
            syncCallback.updateMessages(queue);
        }
    }

    public void flush() {
        doStop = true;
    }

    public interface MessageUpdater {
        public void updateMessages(Deque<MessageHolder> queue);
    }
}
