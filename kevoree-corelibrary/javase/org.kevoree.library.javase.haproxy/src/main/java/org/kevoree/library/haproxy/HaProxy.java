package org.kevoree.library.haproxy;

import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Library;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.framework.AbstractComponentType;
import org.kevoree.framework.FileNIOHelper;

import java.io.File;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 10/04/13
 * Time: 10:00
 */

@Library(name = "Web")
@ComponentType
public class HaProxy extends AbstractComponentType {

    public static void main(String[] args) throws IOException {
        new HaProxy().startProxy();
    }

    private Thread readerSTD = null;
    private Thread readerERROR = null;
    private Process process = null;

    @Start
    public void startProxy() throws IOException {
        File tempExeFile = File.createTempFile("haproxy", "haproxy");
        tempExeFile.deleteOnExit();
        FileNIOHelper.copyFile(HaProxy.class.getClassLoader().getResourceAsStream("haproxy"), tempExeFile);
        tempExeFile.setExecutable(true);

        File tempConfigFile = File.createTempFile("haconfig", "haconfig");
        tempConfigFile.deleteOnExit();
        FileNIOHelper.copyFile(HaProxy.class.getClassLoader().getResourceAsStream("haconfig"), tempConfigFile);
        //TODO INJECT SERVEUR ADDRESS ACCORDING TO QUERY VALUE


        String[] ProcArgs = {tempExeFile.getAbsolutePath(), "-f", tempConfigFile.getAbsolutePath()};
        Process process = Runtime.getRuntime().exec(ProcArgs);

        readerSTD = new OutputReader(process.getInputStream(), false);
        readerERROR = new OutputReader(process.getInputStream(), false);

        readerSTD.start();
        readerERROR.start();
    }

    @Stop
    public void stopProxy() {
        process.destroy();
        readerSTD.stop();
        readerERROR.stop();
    }

}
