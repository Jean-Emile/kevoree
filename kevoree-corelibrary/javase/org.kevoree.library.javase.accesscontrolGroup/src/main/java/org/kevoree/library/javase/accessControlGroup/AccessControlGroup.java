package org.kevoree.library.javase.accessControlGroup;

import jexxus.common.Connection;
import jexxus.common.ConnectionListener;
import jexxus.server.ServerConnection;
import org.kevoree.ContainerRoot;
import org.kevoree.log.Log;
import org.kevoree.tools.accesscontrol.framework.AbstractAccessControlGroupType;
import org.kevoree.tools.accesscontrol.framework.AccessControlException;
import controlbasicGossiper.HelperModelSigned;
import jexxus.client.ClientConnection;
import jexxus.client.UniClientConnection;
import jexxus.common.Delivery;
import jexxus.server.Server;
import org.kevoree.ContainerNode;
import org.kevoree.Group;
import org.kevoree.accesscontrol.AccessControlRoot;
import org.kevoree.adaptation.accesscontrol.api.ControlException;
import org.kevoree.annotation.*;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.library.NodeNetworkHelper;
import org.kevoree.tools.accesscontrol.framework.utils.HelperSignature;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 09/04/13
 * Time: 16:22
 * To change this template use File | Settings | File Templates.
 */
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "8000", optional = true, fragmentDependant = true),
        @DictionaryAttribute(name = "ip", defaultValue = "0.0.0.0", optional = true, fragmentDependant = true),
        @DictionaryAttribute(name = "ssl", defaultValue = "false", vals = {"true", "false"})
})
@GroupType
@Library(name = "JavaSE", names = "Android")
public class AccessControlGroup extends AbstractAccessControlGroupType  implements ConnectionListener
{

    private final byte getModel = 0;
    private final byte pushModel = 1;
    protected Server server = null;
    private boolean starting;
    protected boolean udp = false;
    boolean ssl = false;
    int port = -1;


    @Start
    public void startRestGroup() throws IOException, NoSuchAlgorithmException, ControlException, InvalidKeyException {
        port = Integer.parseInt(this.getDictionary().get("port").toString());
        ssl = Boolean.parseBoolean(this.getDictionary().get("ssl").toString());

        if (udp) {
            server = new Server(this, port, port, ssl);
        } else {
            server = new Server(this, port, ssl);
        }
        Log.info("BasicGroup listen on " + port + "-SSL=" + ssl);
        server.startServer();
        starting = true;
    }

    @Stop
    public void stopRestGroup() {
        server.shutdown();
    }


    @Override
    public void triggerModelUpdate() {
        if (starting) {

            final ContainerRoot modelOption = NodeNetworkHelper.updateModelWithNetworkProperty(this);
            if (modelOption != null) {
                new Thread() {
                    public void run() {
                        try {
                            getModelService().unregisterModelListener(AccessControlGroup.this);
                            getModelService().atomicUpdateModel(modelOption);
                            getModelService().registerModelListener(AccessControlGroup.this);
                        } catch (Exception e) {
                            Log.error("", e);
                        }
                    }
                }.start();
            }
            starting = false;
        } else {
            Group group = getModelElement();
            ContainerRoot currentModel = (ContainerRoot) group.eContainer();
            for (ContainerNode subNode : group.getSubNodes()) {
                if (!subNode.getName().equals(this.getNodeName())) {
                    try {
                        push(currentModel, subNode.getName());
                    } catch (Exception e) {
                        Log.warn("Unable to notify other members of {} group", group.getName());
                    }
                }
            }
        }
    }


    @Override
    public ContainerRoot pull(final String targetNodeName) throws Exception {
        ContainerRoot model = getModelService().getLastModel();
        int PORT = 8000;
        Group groupOption = model.findByPath("groups[" + getName() + "]", Group.class);
        if (groupOption != null) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(groupOption, "port", true, targetNodeName);
            if (portOption != null) {
                try {
                    PORT = Integer.parseInt(portOption);
                } catch (NumberFormatException e) {
                    Log.warn("Attribute \"port\" of {} must be an Integer. Default value ({}) is used", getName(), PORT);
                }
            }
        }
        List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(getModelService().getLastModel(), targetNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
        if (ips.size() > 0) {
            for (String ip : ips) {
                try {
                    return requestModel(ip, PORT, targetNodeName);
                } catch (Exception e) {
                    Log.debug("Unable to pull model on {} using {}:{}", targetNodeName, ip, PORT);
                }
            }
        } else {
            try {
                return requestModel("127.0.0.1", PORT, targetNodeName);
            } catch (Exception e) {
                Log.debug("Unable to pull model on {} using {}:{}", targetNodeName, "127.0.0.1", PORT);
            }
        }
        throw new Exception("Unable to pull model on " + targetNodeName);
    }

    protected ContainerRoot requestModel(String ip, int port, final String targetNodeName) throws IOException, TimeoutException, InterruptedException {
        final Exchanger<ContainerRoot> exchanger = new Exchanger<ContainerRoot>();
        final ClientConnection[] conns = new ClientConnection[1];
        conns[0] = new ClientConnection(new ConnectionListener() {
            @Override
            public void connectionBroken(Connection broken, boolean forced) {
                conns[0].close();
                try {
                    exchanger.exchange(null);
                } catch (InterruptedException e) {
                    Log.error("", e);
                }
            }

            @Override
            public void receive(byte[] data, Connection from) {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                final ContainerRoot root = KevoreeXmiHelper.instance$.loadCompressedStream(inputStream);
                try {
                    exchanger.exchange(root);
                } catch (InterruptedException e) {
                    Log.error("error while waiting model from " + targetNodeName, e);
                } finally {
                    conns[0].close();
                }
            }

            @Override
            public void clientConnected(ServerConnection conn) {
            }

        }, ip, port, ssl);
        conns[0].connect(5000);
        byte[] data = new byte[1];
        data[0] = getModel;
        conns[0].send(data, Delivery.RELIABLE);
        return exchanger.exchange(null, 5000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void connectionBroken(Connection broken, boolean forced) {
    }

    @Override
    public void receive(byte[] data, Connection from) {

        try {
            if (data == null) {
                Log.error("Null rec");
            } else {
                switch (data[0])
                {
                    case getModel:
                    {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        KevoreeXmiHelper.instance$.saveCompressedStream(output, getModelService().getLastModel());
                        from.send(output.toByteArray(), Delivery.RELIABLE);
                    }
                    break;
                    case pushModel:
                    {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                        inputStream.read();

                        byte[] bytessignedModel = HelperModelSigned.loadSignedModelStream(inputStream);
                        ByteArrayInputStream bis = new ByteArrayInputStream(bytessignedModel);
                        ObjectInputStream ois = new ObjectInputStream(bis);

                        try
                        {
                            Object signed = ois.readObject();
                            updateSignedModel(signed);
                        } finally
                        {
                            try {
                                ois.close();
                            } finally {
                                bis.close();
                            }
                        }
                    }
                    break;
                    default:
                        externalProcess(data, from);
                }
            }
        } catch (Exception e) {
            Log.error("Something bad ...", e);
        }

    }

    protected void externalProcess(byte[] data, Connection from) {
        from.close();
    }


    @Override
    public void clientConnected(ServerConnection conn) {

    }


    public void write(ContainerRoot model, String targetNodeName, Object data) throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(pushModel);

        int PORT = 8000;
        Group groupOption = model.findByPath("groups[" + getName() + "]", Group.class);
        if (groupOption != null) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(groupOption, "port", true, targetNodeName);
            if (portOption != null) {
                try {
                    PORT = Integer.parseInt(portOption);
                } catch (NumberFormatException e) {
                    Log.warn("Attribute \"port\" of {} must be an Integer. Default value ({}) is used", getName(), PORT);
                }
            }
        }

        List<String> ips = KevoreePropertyHelper.instance$.getNetworkProperties(model, targetNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
        if(ips.size()== 0){
            Log.warn("no address ip");
            ips.add("127.0.0.1");
        }
        for (String ip : ips) {
            final UniClientConnection[] conns = new UniClientConnection[1];
            conns[0] = new UniClientConnection(new ConnectionListener() {
                @Override
                public void connectionBroken(Connection broken, boolean forced) {
                }

                @Override
                public void receive(byte[] data, Connection from) {
                }

                @Override
                public void clientConnected(ServerConnection conn) {
                }
            }, ip, PORT, ssl);

            ObjectOutputStream oos = new ObjectOutputStream(output);

            try {
                oos.writeObject(data);
                oos.flush();
                conns[0].connect(5000);
                conns[0].send(output.toByteArray(), Delivery.RELIABLE);
                break;
            } catch (Exception e) {
                Log.debug("Unable to write data on {}", targetNodeName, e);
            } finally {
                try {
                    oos.close();
                } finally {
                    output.close();
                }
            }
        }


    }


    @Override
    public void pushPDP(ContainerRoot containerRoot, String node, AccessControlRoot accessControlRoot, PrivateKey privateKey) throws AccessControlException {
        try {
            write(containerRoot, node, HelperSignature.createSignedPDP(accessControlRoot, privateKey));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidKeyException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (SignatureException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Override
    public void pushSignedModel(ContainerRoot containerRoot, String node, PrivateKey privateKey) throws AccessControlException {
        try
        {
            write(containerRoot, node, HelperSignature.createSignedModel(containerRoot, privateKey));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidKeyException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (SignatureException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
