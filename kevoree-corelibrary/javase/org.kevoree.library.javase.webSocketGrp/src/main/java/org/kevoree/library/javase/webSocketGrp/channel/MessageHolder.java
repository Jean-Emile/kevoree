package org.kevoree.library.javase.webSocketGrp.channel;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: leiko
 * Date: 3/22/13
 * Time: 9:49 AM
 * To change this template use File | Settings | File Templates.
 */
public class MessageHolder {

    private List<URI> uris;
    private byte[] data;
    private String nodeName;

    public MessageHolder(String nodeName, byte[] data) {
        this.nodeName = nodeName;
        this.data = data;
        this.uris = new ArrayList<URI>();
    }

    public void addURI(URI uri) {
        this.uris.add(uri);
    }

    public String getNodeName() {
        return this.nodeName;
    }

    public List<URI> getURIs() {
        return this.uris;
    }

    public byte[] getData() {
        return this.data;
    }

    public void clearURIs() {
        this.uris.clear();
    }
}
