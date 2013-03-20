package org.kevoree.library.javase;

import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage;

import java.net.InetSocketAddress;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/19/13
 * Time: 3:50 PM
 */
public interface INetworkSender {

    void sendMessageUnreliable(KevoreeMessage.Message m, InetSocketAddress addr);

    boolean sendMessage(KevoreeMessage.Message m, InetSocketAddress addr);
}
