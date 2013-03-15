package org.kevoree.library.javase.autoBasicGossiper;

import org.kevoree.ContainerRoot;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.GroupType;
import org.kevoree.api.service.core.handler.UUIDModel;
import org.kevoree.library.BasicGroup;
import org.kevoree.library.NodeNetworkHelper;
import org.kevoree.library.javase.jmdns.JmDNSComponent;
import org.kevoree.library.javase.jmdns.JmDNSListener;
import org.kevoree.merger.KevoreeMergerComponent;

import java.io.IOException;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 13/02/13
 * Time: 11:48
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@GroupType
@DictionaryType(
        @DictionaryAttribute(name = "ipv4Only", vals = {"true", "false"}, defaultValue = "true")
)
public class AutoJmDNSBasicGroup extends BasicGroup implements JmDNSListener {

    private JmDNSComponent jmDnsComponent;
    private KevoreeMergerComponent mergerComponent;

    @Override
    public void startRestGroup() throws IOException {
        super.startRestGroup();
        jmDnsComponent = new JmDNSComponent(this, this, this.getDictionary().get("ip").toString(), Integer.parseInt(this.getDictionary().get("port").toString()), getDictionary().get("ipv4Only").toString().equalsIgnoreCase("true"));
        jmDnsComponent.start();
        mergerComponent = new KevoreeMergerComponent();
    }

    @Override
    public void stopRestGroup() {
        super.stopRestGroup();
        jmDnsComponent.stop();
    }

    @Override
    public void notifyNewSubNode(String remoteNodeName) {
        logger.debug("new remote node discovered, try to pull the model from {}", remoteNodeName);
        try {
            push(getModelService().getLastModel(), remoteNodeName);
        } catch (Exception e) {
            logger.debug("unable to notify other members of the group " + getName(), e);
        }
    }

    public synchronized boolean updateModel(ContainerRoot model) {
        return compareAndSwapOrMerge(getModelService().getLastUUIDModel(), model, 20);
    }

    private boolean compareAndSwapOrMerge(UUIDModel uuidModel, ContainerRoot model, int maxTries) {
        if (uuidModel != null && model != null && maxTries > 0) {
            try {
                getModelService().compareAndSwapModel(uuidModel, model);
                return true;
            } catch (Exception e) {
                logger.debug("Unable to compare and swap model", e);
                if (maxTries > 0) {
                    logger.debug("Try to merge model with the current one");
                    uuidModel = getModelService().getLastUUIDModel();
                    model = mergerComponent.merge(uuidModel.getModel(), model);
                    return compareAndSwapOrMerge(uuidModel, model, maxTries - 1);
                } else {
                    return false;
                }
            }
        } else {
            logger.error("Unable to compare and swap or merge model using undefined models");
            return false;
        }
    }

    @Override
    protected void localUpdateModel(final ContainerRoot modelOption) {
        final UUIDModel uuidModel = getModelService().getLastUUIDModel();
        new Thread() {
            public void run() {
                getModelService().unregisterModelListener(AutoJmDNSBasicGroup.this);
                if (!compareAndSwapOrMerge(uuidModel, modelOption, 20)) {
                    logger.warn("Unable to update network properties after 20 tries");
                }
                getModelService().registerModelListener(AutoJmDNSBasicGroup.this);
            }
        }.start();
    }

    @Override
    public void triggerModelUpdate() {
        if (starting) {
            final UUIDModel uuidModel = getModelService().getLastUUIDModel();
            final ContainerRoot modelOption = NodeNetworkHelper.updateModelWithNetworkProperty(this);
            new Thread() {
                public void run() {
                    getModelService().unregisterModelListener(AutoJmDNSBasicGroup.this);
                    if (!compareAndSwapOrMerge(uuidModel, modelOption, 20)) {
                        logger.warn("Unable to update network properties after 20 tries");
                    }
                    getModelService().registerModelListener(AutoJmDNSBasicGroup.this);
                }
            }.start();
            starting = false;
        } else {
            broadcast(getModelService().getLastModel());
        }
    }
}
