package org.kevoree.library.camel.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.kevoree.annotation.ChannelTypeFragment;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.Library;
import org.kevoree.framework.KevoreeChannelFragment;
import org.kevoree.framework.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 29/01/13
 * Time: 18:32
 *
 * @author Erwan Daubert
 * @version 1.0
 */

@Library(name = "JavaSE")
@ChannelTypeFragment
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "10000", optional = true, fragmentDependant = true)
})
// TODO define upper bounds to 1
public class CamelJettyChannelService extends CamelJettyChannelMessage {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Random random = new Random();

    @Override
    protected void buildRoutes(RouteBuilder routeBuilder) {
        routeBuilder.from("kchannel:input")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (getBindedPorts().isEmpty() && getOtherFragments().isEmpty()) {
                            logger.debug("No consumer, msg lost=" + exchange.getIn().getBody());
                        } else {
                            // default behavior is round robin
                            int rang = random.nextInt(getBindedPorts().size() + getOtherFragments().size());
                            Message message = (Message) exchange.getIn().getBody();
                            if (rang < getBindedPorts().size()) {
                                logger.debug("select rang: {} for channel {}", new Object[]{rang, CamelJettyChannelService.this.getName()});
                                logger.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                Object result = forward(getBindedPorts().get(rang), message);
                                // forward the result
                                exchange.getOut().setBody(result);
                            } else {
                                rang = rang - getBindedPorts().size();
                                logger.debug("select rang: {} for channel {}", new Object[]{rang, CamelJettyChannelService.this.getName()});
                                KevoreeChannelFragment cf = getOtherFragments().get(rang);
                                logger.debug("Trying to send message on {}", getOtherFragments().get(rang).getNodeName());
                                List<String> addresses = getAddresses(cf.getNodeName());
                                if (addresses.size() > 0) {
                                    for (String address : addresses) {
                                        try {
                                            Object result = getContext().createProducerTemplate().requestBody("jetty:http://" + address + ":" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), message);
                                            // forward the result
                                            exchange.getOut().setBody(result);
                                            break;
                                        } catch (Exception e) {
                                            logger.debug("Unable to send data to components on {} using {} as address", cf.getNodeName(), "jetty:http://" + address + ":" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), e);
                                        }
                                    }
                                } else {
                                    try {
                                        Object result = getContext().createProducerTemplate().requestBody("jetty:http://127.0.0.1:" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), message);
                                        // forward the result
                                        exchange.getOut().setBody(result);
                                    } catch (Exception e) {
                                        logger.debug("Unable to send data to components on {} using {} as address", cf.getNodeName(), "jetty:http://127.0.0.1:" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), e);
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
                    routeBuilder.from("jetty:http://:" + address + port).
                            process(new Processor() {
                                public void process(Exchange exchange) throws Exception {
                                    // default behavior is round robin
                                    int rang = random.nextInt(getBindedPorts().size());
                                    logger.debug("select rang: {} for channel {}", new Object[]{rang, CamelJettyChannelService.this.getName()});
                                    logger.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                    Object result = forward(getBindedPorts().get(rang), (Message) exchange.getIn().getBody());
                                    // forward result
                                    exchange.getOut().setBody(result);
                                }
                            });
                } catch (Exception e) {
                    logger.debug("Fail to manage route {}", "http://" + address + ":" + port, e);
                }
            }
        } else {
            try {
                routeBuilder.from("jetty:http://127.0.0.1:" + port).
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // default behavior is round robin
                                int rang = random.nextInt(getBindedPorts().size());
                                logger.debug("select rang: {} for channel {}", new Object[]{rang, CamelJettyChannelService.this.getName()});
                                logger.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                Object result = forward(getBindedPorts().get(rang), (Message) exchange.getIn().getBody());
                                // forward result
                                exchange.getOut().setBody(result);
                            }
                        });
            } catch (Exception e) {
                logger.debug("Fail to manage route {}", "http://127.0.0.1:" + port, e);
            }
        }
    }
}
