package org.kevoree.library.WakeonLAN;

import org.kevoree.annotation.*;
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
 * Time: 11:43
 * To change this template use File | Settings | File Templates.
 */

@Library(name = "JavaSE")
@ComponentType
@Provides({
        @ProvidedPort(name = "service", type = PortType.SERVICE, className = WakeOnLAnService.class)
})
public class WakeOnLANComponent extends AbstractComponentType implements WakeOnLAnService {


    /**
     * Have a machine Wake-on-LAN
     * Sends the magic sequence in an UDP datagram to a machine.
     * The magic sequence is 6 0xFF followed by the machine's ethernet address
     * repeated at least 16 times.
     */

    /**
     * Send magic packet to a computer
     * @param ipaddr The machine's Internet Protocol address.
     * @param ethernet The machine's Ethernet address.
     */
    private static void wake(InetAddress ipaddr, byte[] ethernet) throws java.io.IOException {
        byte[] magic = new byte[6 + 16 * ethernet.length];

        for (int i=0; i < 6; i++)
            magic[i] = (byte)0xff;

        for (int i = 6; i < magic.length; i += ethernet.length)
            System.arraycopy(ethernet, 0, magic, i, ethernet.length);

        // send it to the discard port, 9 so that the machines TCP stack will discard it.
        // we could send it to the echo port, 7, so that the machine returns the packet,
        // if it is already online. Could also ping the machine with the magic sequence as data.
        DatagramPacket gram = new DatagramPacket(magic, magic.length, ipaddr, 9);
        new DatagramSocket().send(gram);
    }
    /**
     * Parse an ethernet address string into its bytes.
     * @param address The address as hexadecimal represented bytes separated with '-'
     * @return The address as a byte array.
     */
    private static byte[] ethernet(String address) {
        StringTokenizer parts = new StringTokenizer(address, "-");

        byte[] ab = new byte[parts.countTokens()];
        for(int i=0; i < ab.length; i++)
            ab[i] = (byte)Integer.parseInt(parts.nextToken(), 16);

        return ab;
    }

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



    @Port(name = "service", method = "send")
    @Override
    public void send(String mac)
    {
        InetAddress ia = null;
        try {
            ia = InetAddress.getLocalHost();

            byte[] eth = ethernet(mac);
            System.out.print("Sending ");

            String s;
            for(int i = 0; i < eth.length; i++) {
                s = Integer.toHexString(eth[i]);
                while(s.length() < 2)
                    s = "0" + s;

                System.out.print(s.substring(s.length() - 2));
                if (i < (eth.length -1)){
                    System.out.print("-");
                }
            }
            System.out.println(" to " + ia.getHostAddress());

            wake(ia, eth);
        } catch (UnknownHostException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
