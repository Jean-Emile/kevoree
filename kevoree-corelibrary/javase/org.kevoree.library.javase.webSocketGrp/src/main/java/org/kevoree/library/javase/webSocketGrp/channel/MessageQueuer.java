package org.kevoree.library.javase.webSocketGrp.channel;

import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/21/13
 * Time: 5:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class MessageQueuer {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<String, WebSocketClient> clients;
    private Deque<MessageHolder> queue;
    private int maxQueued;
    private boolean doStop = false;

    public MessageQueuer(int maxQueued, final Map<String, WebSocketClient> clients) {
        this.maxQueued = maxQueued;
        this.clients = clients;
        this.queue = new ArrayDeque<MessageHolder>();

        // create the thread that will handle message sending
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!doStop) {
                    // try to send message to the last item in queue
                    final MessageHolder msg = queue.pollLast();
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
                                client.connectBlocking();
                                client.send(msg.getData());
                                clients.put(msg.getNodeName(), client);

                                strURI = uri.toString();
                                sent = true;

                            } catch (InterruptedException e) {
                                // connection failed, we will try it later
                                logger.debug("Unable to connect to {}, message still in queue", uri);
                            }
                        }

                        if (sent) {
                            logger.debug("Message from queue delivered to {}", strURI);
                        } else {
                            // put that message back in the queue
                            queue.addLast(msg);
                        }
                    }
                }
            }
        }).start();
    }

    public void addToQueue(MessageHolder msg) {
        synchronized (queue) {
            queue.addLast(msg);
        }
    }

    public void stop() {
        doStop = true;
    }
}
