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
@ComponentType
public class ComponentApache2224 extends AbstractComponentType {

    NativeExecManager manager = new NativeExecManager();
    @Start
    public void startApache() {
        try {
            manager.setNameNativeExec("apache2224");
            manager.getProperties().put("User", "nobody");
            manager.getProperties().put("Group","bin");
            manager.setPort(Integer.parseInt(getDictionary().get("port").toString()));
            manager.setDocumentRoot(getDictionary().get("ServerRoot").toString());
            manager.install_generics();
            manager.install_lib("libapr-1.so.0");
            manager.install_lib("libaprutil-1.so.0");
            manager.install_lib("libexpat.so.1");
            manager.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Stop
    public void stopApache() {
        try {
            manager.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Update
    public void updateApache() {
        manager.setPort(Integer.parseInt(getDictionary().get("port").toString()));
        manager.setDocumentRoot(getDictionary().get("ServerRoot").toString());

        try {
            manager.updateConfiguration();
            manager.restart();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
