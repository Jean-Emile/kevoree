package org.kevoree.library.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.kevoree.annotation.*;
import org.kevoree.framework.KevoreeChannelFragment;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.framework.message.Message;
import org.kevoree.library.camel.framework.AbstractKevoreeCamelChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 10/05/12
 * Time: 15:25
 */

@Library(name = "JavaSE")
@ChannelTypeFragment
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "10000", optional = true, fragmentDependant = true)
})
public class CamelNetty extends AbstractKevoreeCamelChannelType {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    protected int port;

    /* found a solution to remobe */
    @Start
    @Override
    public void startCamelChannel() throws Exception {
        port = parsePortNumber(getNodeName());
        super.startCamelChannel();
    }

    @Stop
    @Override
    public void stopCamelChannel() throws Exception {
        super.stopCamelChannel();
    }

    @Update
    @Override
    public void updateCamelChannel() throws Exception {
        if (!getDictionary().get("port").toString().equals(port + "")) {
            super.updateCamelChannel();
        }
    }

    @Override
    protected void buildRoutes(RouteBuilder routeBuilder) {
        routeBuilder.from("kchannel:input")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (getBindedPorts().isEmpty() && getOtherFragments().isEmpty()) {
                            logger.debug("No consumer, msg lost=" + exchange.getIn().getBody());
                        } else {
                            for (org.kevoree.framework.KevoreePort p : getBindedPorts()) {
                                if (exchange.getIn().getBody() instanceof Message) {
                                    forward(p, (Message) exchange.getIn().getBody());
                                }
                            }
                            for (KevoreeChannelFragment cf : getOtherFragments()) {
                                List<String> addresses = getAddresses(cf.getNodeName());
                                if (addresses.size() > 0) {
                                    for (String address : addresses) {
                                        try {
                                            getContext().createProducerTemplate().sendBody("netty:tcp://" + address + ":" + parsePortNumber(cf.getNodeName()), exchange.getIn().getBody());
                                            break;
                                        } catch (Exception e) {
                                            logger.debug("Unable to send data to components on {} using {} as address", cf.getNodeName(), "netty:tcp://" + address + ":" + parsePortNumber(cf.getNodeName()), e);
                                        }
                                    }
                                } else {
                                    try {
                                        getContext().createProducerTemplate().sendBody("netty:tcp://127.0.0.1:" + parsePortNumber(cf.getNodeName()), exchange.getIn().getBody());
                                    } catch (Exception e) {
                                        logger.debug("Unable to send data to components on {} using {} as address", cf.getNodeName(), "netty:tcp://127.0.0.1:" + parsePortNumber(cf.getNodeName()), e);
                                    }
                                }
                            }
                        }
                    }
                }
                );
        List<String> addresses = getAddresses(getNodeName());
        if (addresses.size() > 0) {
            for (String address : addresses) {
                try {
                    routeBuilder.from("netty:tcp://" + address + ":" + port + "?sync=true").
                            process(new Processor() {
                                public void process(Exchange exchange) throws Exception {
                                    exchange.getOut().setBody("result Async TODO");
                                    for (org.kevoree.framework.KevoreePort p : getBindedPorts()) {
                                        if (exchange.getIn().getBody() instanceof Message) {
                                            forward(p, (Message) exchange.getIn().getBody());
                                        }
                                    }
                                }
                            });
                } catch (Exception e) {
                    logger.debug("Fail to manage route {}", "netty:tcp://" + address + ":" + port + "?sync=true", e);
                }
            }
        } else {
            try {
                routeBuilder.from("netty:tcp://127.0.0.1:" + port + "?sync=true").
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody("result Async TODO");
                                for (org.kevoree.framework.KevoreePort p : getBindedPorts()) {
                                    if (exchange.getIn().getBody() instanceof Message) {
                                        forward(p, (Message) exchange.getIn().getBody());
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                logger.debug("Fail to manage route {}", "netty:tcp://127.0.0.1:" + port + "?sync=true", e);
            }
        }
    }


    public List<String> getAddresses(String remoteNodeName) {
        return KevoreePropertyHelper.getNetworkProperties(getModelService().getLastModel(), remoteNodeName, org.kevoree.framework.Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP());
    }

    public int parsePortNumber(String nodeName) {
        Option<String> portOption = KevoreePropertyHelper.getProperty(getModelElement(), "port", true, nodeName);
        int port = 9000;
        if (portOption.isDefined()) {
            try {
                port = Integer.parseInt(portOption.get());
            } catch (NumberFormatException e) {
                logger.warn("Attribute \"port\" of {} is not an Integer, Default value ({}) is returned", getName(), port);
            }
        } else {
            logger.info("Attribute \"port\" of {} is not set for {}, Default value ({}) is returned", new String[]{getName(), nodeName, port + ""});
        }
        return port;
    }


}
