package org.kevoree.library.javase.webSocketGrp.net;

import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage;
import org.kevoree.library.javase.INetworkSender;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/19/13
 * Time: 3:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class NetworkSender implements INetworkSender {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void sendMessageUnreliable(KevoreeMessage.Message m, InetSocketAddress addr) {
        URI uri = URI.create("ws://"+addr.getAddress().getHostAddress()+":"+addr.getPort()+"/");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(2);
            m.writeTo(output);
            byte[] message = output.toByteArray();
            output.close();
            WebSocketClient client = new WebSocketClient(uri);
            client.connectBlocking();
            client.send(message);
            client.close();
        } catch (Exception e) {
            logger.error("Unable to connect to server {}", uri, e);
        }
    }

    @Override
    public boolean sendMessage(KevoreeMessage.Message m, InetSocketAddress addr) {
        URI uri = URI.create("ws://"+addr.getAddress().getHostAddress()+":"+addr.getPort()+"/");
        WebSocketClient conn = null;
        try {
            conn = new WebSocketClient(uri);
            conn.connectBlocking();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(2);
            m.writeTo(output);
            conn.send(output.toByteArray());
            output.close();
            logger.debug("message ({}) sent to {}", m.getContentClass() , addr.toString());
            return true;

        } catch (Exception e) {
            logger.debug("", e);
        } finally {
            if (conn != null && conn.getConnection().isOpen()) {
                try {
                    conn.close();
                } catch (Exception ignored){
                }
            }
        }
        return false;
    }
}
