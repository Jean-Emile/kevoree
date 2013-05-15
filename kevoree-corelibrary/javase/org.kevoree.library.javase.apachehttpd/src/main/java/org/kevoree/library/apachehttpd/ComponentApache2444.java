package org.kevoree.library.apachehttpd;


import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;

import java.io.IOException;

/**
 * @author jed
 */
@Library(name = "JavaSE")
@DictionaryType({
        @DictionaryAttribute(name = "port", defaultValue = "8080", optional = false),
        @DictionaryAttribute(name = "ServerRoot", defaultValue = "", optional = false)
})
//@ComponentType
public class ComponentApache2444 extends AbstractComponentType {

    ApacheManager  manager = new ApacheManager("apache2444");
    @Start
    public void startApache2055() {
        try {

            manager.setPort(Integer.parseInt(getDictionary().get("port").toString()));
            manager.setDocumentRoot(getDictionary().get("ServerRoot").toString());
            manager.install();
            manager.start();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Stop
    public void stopApache2055() {
        try {
            manager.stop();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Update
    public void updateApache2055() {
        manager.setPort(Integer.parseInt(getDictionary().get("port").toString()));
        manager.setDocumentRoot(getDictionary().get("ServerRoot").toString());

        try {
            manager.updateConfiguration();
            manager.restart();

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


}
