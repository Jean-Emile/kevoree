package org.kevoree.library.nanohttpd;

import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;
import org.kevoree.framework.MessagePort;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 28/05/13
 * Time: 17:29
 * To change this template use File | Settings | File Templates.
 */

@Library(name = "JavaSE")
@ComponentType
@DictionaryType({
        @DictionaryAttribute(name = "port" , defaultValue = "8080"),
        @DictionaryAttribute(name = "timeout" , defaultValue = "5000", optional = true)
})
@Requires({
        @RequiredPort(name = "handler", type = PortType.MESSAGE)
})
@Provides({
        @ProvidedPort(name = "response", type = PortType.MESSAGE)
})
public class NanoHTTPDComponent extends AbstractComponentType {

    NanoHTTPDKevoree bootstrap = null;

    @Start
    public void start() {
        bootstrap = new NanoHTTPDKevoree(this.getPortByName("handler", MessagePort.class),Integer.parseInt(getDictionary().get("port").toString()));
        try {
            bootstrap.start();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Stop
    public void stop() {
        bootstrap.stop();
    }

    @Update
    public void update() {
        stop();
        start();
    }

    @Port(name = "response")
    public void responseHandler(Object r) {
        if (bootstrap != null) {
            bootstrap.responseHandler(r);
        }
    }


}
