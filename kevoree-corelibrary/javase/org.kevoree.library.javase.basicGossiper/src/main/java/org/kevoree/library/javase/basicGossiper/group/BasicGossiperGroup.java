package org.kevoree.library.javase.basicGossiper.group;

import jexxus.common.Connection;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.Group;
import org.kevoree.KevoreeFactory;
import org.kevoree.annotation.*;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.impl.DefaultKevoreeFactory;
import org.kevoree.library.BasicGroup;
import org.kevoree.library.basicGossiper.protocol.gossip.Gossip;
import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage;
import org.kevoree.library.javase.basicGossiper.GossiperComponent;
import org.kevoree.library.javase.basicGossiper.GossiperPeriodic;
import org.kevoree.library.javase.basicGossiper.GossiperProcess;
import org.kevoree.library.javase.basicGossiper.Serializer;
import org.kevoree.library.javase.conflictSolver.AlreadyPassedPrioritySolver;
import org.kevoree.log.Log;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Erwan Daubert
 */
@Library(name = "JavaSE", names = "Android")
@GroupType
@DictionaryType({
        @DictionaryAttribute(name = "interval", defaultValue = "20000", optional = true)
})
public class BasicGossiperGroup extends BasicGroup implements GossiperComponent {

    protected DataManagerForGroup dataManager;
    protected GossiperPeriodic actor;
    protected GroupScorePeerSelector selector;
    private GossiperProcess processValue;

    @Start
    public void startGossiperGroup() throws IOException {
        Log.debug("START BasicGossiper");
        udp = true;
        Long timeoutLong = Long.parseLong((String) this.getDictionary().get("interval"));
        Serializer serializer = new GroupSerializer(this.getModelService());
        dataManager = new DataManagerForGroup(this.getName(), this.getNodeName(), this.getModelService(), new AlreadyPassedPrioritySolver(getKevScriptEngineFactory()));
        processValue = new GossiperProcess(this, dataManager, serializer, false);
        selector = new GroupScorePeerSelector(timeoutLong, this.currentCacheModel, this.getNodeName());
        Log.debug("{}: initialize GossiperActor", this.getName());
        actor = new GossiperPeriodic(this, timeoutLong, selector, processValue);
        processValue.start();
        actor.start();
        starting = true;
        super.startRestGroup();
    }

    protected void externalProcess(byte[] data, Connection from) {
        try {
            ByteArrayInputStream stin = new ByteArrayInputStream(data);
            stin.read();
            KevoreeMessage.Message msg = KevoreeMessage.Message.parseFrom(stin);
            Log.debug("Rec Some MSG {}->{}->{}", new String[]{msg.getContentClass(), msg.getDestName(), msg.getDestNodeName()});
            if (!msg.getDestNodeName().equals(getNodeName())) {
                processValue.receiveRequest(msg);
            } else {
                Log.debug("message coming from itself, we don't need to manage it");
            }
        } catch (Exception e) {
            Log.error("", e);
        }
    }

    @Stop
    public void stopGossiperGroup() {
        Log.debug("STOP BasicGossiper");
        super.stopRestGroup();
        if (actor != null) {
            actor.stop();
            actor = null;
        }
        if (selector != null) {
            selector = null;
        }
        if (processValue != null) {
            processValue.stop();
            processValue = null;
        }
        if (dataManager != null) {
            dataManager = null;
        }
    }

    @Update
    public void updateGossiperGroup() throws IOException {
        if (port != Integer.parseInt(this.getDictionary().get("port").toString())) {
            Log.info("try to update configuration of {}", this.getName());
            stopGossiperGroup();
            startGossiperGroup();
        }
    }

    private KevoreeFactory factory = new DefaultKevoreeFactory();
    protected AtomicReference<ContainerRoot> currentCacheModel = new AtomicReference<ContainerRoot>(factory.createContainerRoot());

    /*@Override
    public boolean afterLocalUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        currentCacheModel.set(proposedModel);
        return super.afterLocalUpdate(currentModel, proposedModel);
    }*/

    @Override
    public List<String> getAddresses(String remoteNodeName) {
        return KevoreePropertyHelper.instance$.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP());
    }

    @Override
    public int parsePortNumber(String nodeName) {
        Group groupOption = currentCacheModel.get().findByPath("groups[" + getName() + "]", Group.class);
        int port = 8000;
        if (groupOption != null) {
            String portOption = KevoreePropertyHelper.instance$.getProperty(groupOption, "port", true, nodeName);
            if (portOption != null) {
                try {
                    port = Integer.parseInt(portOption);
                } catch (NumberFormatException e) {
                    Log.warn("Attribute \"port\" of {} is not an Integer, default value ({}) is used.", getName(), port+"");
                }
            }
        } else {
            Log.warn("There is no group named {}, default value ({}) is used.", getName(), port+"");
        }
        return port;
    }


    @Override
    public void localNotification(Object data) {
        // NO OP
    }


    protected void notifyPeersInternal(List<String> l) {
        KevoreeMessage.Message.Builder messageBuilder = KevoreeMessage.Message.newBuilder().setDestName(getName()).setDestNodeName(getNodeName());
        messageBuilder.setContentClass(Gossip.UpdatedValueNotification.class.getName()).setContent(Gossip.UpdatedValueNotification.newBuilder().build().toByteString());
        for (String peer : l) {
            if (!peer.equals(getNodeName())) {
                int port = parsePortNumber(peer);
                boolean sent = false;
                for (String address : getAddresses(peer)) {
                    sent = processValue.netSender().sendMessage/*Unreliable*/(messageBuilder.build(), new InetSocketAddress(address, port));
                    if (sent) {
                        break;
                    }
                }
                if (!sent) {
                    processValue.netSender().sendMessage/*Unreliable*/(messageBuilder.build(), new InetSocketAddress("127.0.0.1", port));
                }

            }
        }
    }

    @Override
    protected void localUpdateModel(final ContainerRoot modelOption) {
        new Thread() {
            public void run() {
                getModelService().unregisterModelListener(BasicGossiperGroup.this);
                getModelService().atomicUpdateModel(modelOption);
                getModelService().registerModelListener(BasicGossiperGroup.this);
            }
        }.start();
    }

    @Override
    protected void broadcast(ContainerRoot model) {
        currentCacheModel.set(model);
        for (Object o : currentCacheModel.get().getGroups()) {
            Group g = (Group) o;
            if (g.getName().equals(this.getName())) {
                List<String> peers = new ArrayList<String>(g.getSubNodes().size());
                for (ContainerNode node : g.getSubNodes()) {
                    peers.add(node.getName());
                }
                notifyPeersInternal(peers);
            }
        }
    }

    @Override
    public void triggerModelUpdate() {
        if (starting) {
            final ContainerRoot modelOption = org.kevoree.library.NodeNetworkHelper.updateModelWithNetworkProperty(this);
            if (modelOption != null) {
                getModelService().unregisterModelListener(this);
                getModelService().atomicUpdateModel(modelOption);
                getModelService().registerModelListener(this);
            }
            starting = false;
        }
        broadcast(getModelService().getLastModel());
    }


}
