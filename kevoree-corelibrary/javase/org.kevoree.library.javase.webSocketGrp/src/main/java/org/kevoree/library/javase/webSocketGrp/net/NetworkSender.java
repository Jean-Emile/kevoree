package org.kevoree.library.javase.webSocketGrp.net;

import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage;
import org.kevoree.library.javase.INetworkSender;
import org.kevoree.library.javase.webSocketGrp.client.WebSocketClient;
import org.kevoree.log.Log;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/19/13
 * Time: 3:57 PM
 */
public class NetworkSender implements INetworkSender {

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
            Log.error("Unable to connect to server {}",e, uri.toString());
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
            Log.debug("message ({}) sent to {}", m.getContentClass().toString(), addr.toString());
            return true;

        } catch (Exception e) {
            Log.debug("", e);
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
