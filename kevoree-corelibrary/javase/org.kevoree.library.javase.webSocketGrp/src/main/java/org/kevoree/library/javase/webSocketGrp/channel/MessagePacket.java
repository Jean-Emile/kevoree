package org.kevoree.library.javase.webSocketGrp.channel;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/25/13
 * Time: 2:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class MessagePacket implements Serializable {

    public String recipient;
    public Object content;

    public MessagePacket(String rec, Object data) {
        this.recipient = rec;
        this.content = data;
    }

    public byte[] getByteContent() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(baos);
        out.writeObject(content);
        out.close();

        return baos.toByteArray();
    }
}
