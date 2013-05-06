package org.kevoree.library.javase.webserver;

import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;
import org.kevoree.framework.MessagePort;

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 14/10/11
 * Time: 08:40
 */

@Library(name = "JavaSE")
@ComponentType
@DictionaryType({
        @DictionaryAttribute(name = "urlpattern",optional = true, defaultValue = "/")
})
@Provides({
    @ProvidedPort(name = "request", type = PortType.MESSAGE)
})
@Requires({
        @RequiredPort(name = "filtered", type = PortType.MESSAGE)
})
public class URLFilter extends AbstractComponentType {

    URLHandler handler = null;
    
    @Start
    public void startHandler() {
        handler = new URLHandler();
        handler.initRegex(this.getDictionary().get("urlpattern").toString());
    }

    @Stop
    public void stopHandler() {
        handler = null;
    }

    @Update
    public void updateHandler() {
        stopHandler();
        startHandler();
    }
    
    @Port(name = "request")
    public void requestHandler(Object param){
        if(handler != null && param instanceof KevoreeHttpRequest){
            KevoreeHttpRequest filtered = handler.check((KevoreeHttpRequest)param);
            if(filtered != null){
                this.getPortByName("filtered", MessagePort.class).process(filtered);
            }
        }
    }

}
