package org.kevoree.library.WakeonLAN;

import org.kevoree.annotation.Port;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.annotation.Update;
import org.kevoree.framework.AbstractComponentType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
 * To change this template use File | Settings | File Templates.
 */
public class SystemUtilsComponent extends AbstractComponentType implements ISystemUtils {




    @Start
    public void start()
    {

    }

    @Stop
    public void stop()
    {

    }

    @Update
    public void update()
    {

    }



    @Port(name = "service", method = "shutdown")
    @Override
    public void shutdown()
    {
        String shutdownCmd = "shutdown -s" ;
        try {
            Process child = Runtime.getRuntime().exec(shutdownCmd);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
