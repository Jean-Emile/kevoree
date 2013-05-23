package org.kevoree.library.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.kevoree.annotation.ChannelTypeFragment;
import org.kevoree.annotation.Library;
import org.kevoree.framework.KevoreeChannelFragment;
import org.kevoree.framework.message.Message;
import org.kevoree.log.Log;

import java.util.List;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 10/05/12
 * Time: 15:25
 */
@Library(name = "JavaSE")
@ChannelTypeFragment
public class CamelNettyService extends CamelNetty {

    private Random random = new Random();

    @Override
    protected void buildRoutes(RouteBuilder routeBuilder) {
        routeBuilder.from("kchannel:input")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (getBindedPorts().isEmpty() && getOtherFragments().isEmpty()) {
                            Log.debug("No consumer, msg lost=" + exchange.getIn().getBody());
                        } else {
                            // default behavior is round robin
                            int rang = random.nextInt(getBindedPorts().size() + getOtherFragments().size());
                            Message message = (Message) exchange.getIn().getBody();
                            if (rang < getBindedPorts().size()) {
                                Log.debug("select rang: {} for channel {}", rang+"", CamelNettyService.this.getName());
                                Log.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                Object result = forward(getBindedPorts().get(rang), message);
                                // forward the result
                                exchange.getOut().setBody(result);
                            } else {
                                rang = rang - getBindedPorts().size();
                                Log.debug("select rang: {} for channel {}", rang+"", CamelNettyService.this.getName());
                                KevoreeChannelFragment cf = getOtherFragments().get(rang);
                                Log.debug("trying to send message on {}", cf.getNodeName());
                                List<String> addresses = getAddresses(cf.getNodeName());
                                if (addresses.size() > 0) {
                                    for (String address : addresses) {
                                        try {
                                            Object result = getContext().createProducerTemplate().requestBody("netty:tcp://" + address + ":" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), message);
                                            // forward the result
                                            exchange.getOut().setBody(result);
                                            break;
                                        } catch (Exception e) {
                                            Log.debug("Unable to send data to components on {} using {} as address",e, cf.getNodeName(), "netty:tcp://" + address + ":" + parsePortNumber(cf.getNodeName()));
                                        }
                                    }
                                } else {
                                    try {
                                        Object result = getContext().createProducerTemplate().requestBody("netty:tcp://127.0.0.1:" + parsePortNumber(getOtherFragments().get(rang).getNodeName()), message);
                                        // forward the result
                                        exchange.getOut().setBody(result);
                                    } catch (Exception e) {
                                        Log.debug("Unable to send data to components on {} using {} as address",e, cf.getNodeName(), "netty:tcp://127.0.0.1:" + parsePortNumber(cf.getNodeName()));
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
                                    // default behavior is round robin
                                    int rang = random.nextInt(getBindedPorts().size());
                                    Log.debug("select rang: {} for channel {}", rang+"", CamelNettyService.this.getName());
                                    Log.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                    Object result = forward(getBindedPorts().get(rang), (Message) exchange.getIn().getBody());
                                    // forward result
                                    exchange.getOut().setBody(result);
                                }
                            });
                } catch (Exception e) {
                    Log.debug("Fail to manage route {}",e, "netty:tcp://" + address + ":" + port + "?sync=true");
                }
            }
        } else {
            try {
                routeBuilder.from("netty:tcp://127.0.0.1:" + port + "?sync=true").
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // default behavior is round robin
                                int rang = random.nextInt(getBindedPorts().size());
                                Log.debug("select rang: {} for channel {}", rang+"", CamelNettyService.this.getName());
                                Log.debug("send message to {}", getBindedPorts().get(rang).getComponentName());
                                Object result = forward(getBindedPorts().get(rang), (Message) exchange.getIn().getBody());
                                // forward result
                                exchange.getOut().setBody(result);
                            }
                        });
            } catch (Exception e) {
                Log.debug("Fail to manage route {}",e, "netty:tcp://127.0.0.1:" + port + "?sync=true");
            }
        }
    }
}
