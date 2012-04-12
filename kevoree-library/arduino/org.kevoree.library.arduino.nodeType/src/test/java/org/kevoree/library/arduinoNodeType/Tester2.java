package org.kevoree.library.arduinoNodeType;

import org.kevoree.ContainerRoot;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.tools.aether.framework.NodeTypeBootstrapHelper;

import java.io.IOException;

public class Tester2 {

    public static void main(String[] args) throws IOException {

        // TODO need to add manually the path of the rxtx dynamic library : -Djava.library.path=...


        //ArduinoHomeFinder.checkArduinoHome();
        //System.out.println(ArduinoToolChainExecutables.getAVR_GCC());
        //System.setProperty("arduino.home", "/Applications/Arduino.app/Contents/Resources/Java");
        //System.setProperty("avr.bin","/Applications/Arduino.app/Contents/Resources/Java/hardware/tools/avr/bin");
        //System.setProperty("avrdude.config.path", "/Applications/Arduino.app/Contents/Resources/Java/hardware/tools/avr/etc/avrdude.conf");
        // System.setProperty("serial.port", "/dev/tty.usbmodem621");



        
        ContainerRoot   model = KevoreeXmiHelper.load("/home/jed/Desktop/model.kev") ;

      //  ContainerRoot model = KevoreeXmiHelper.load("/Users/duke/Desktop/kev.kev");

        ArduinoNode node = new ArduinoNode();
        node.setNodeName("node0");

        //FOR TEST
        NodeTypeBootstrapHelper bs = new NodeTypeBootstrapHelper();
        node.setBootStrapperService(bs);



        node.getDictionary().put("boardTypeName", "uno");
        //node.getDictionary().put("boardPortName","/dev/tty.usbserial-A400g2se");
//        node.getDictionary().put("pmem","EEPROM");
        // node.getDictionary().put("boardPortName","/dev/tty.usbserial-A400g2se");


        node.getDictionary().put("incremental", "true");
        node.startNode();
        node.push("node0", model, "/dev/ttyACM0");

    }

}
