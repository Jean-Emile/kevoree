package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.api.service.core.handler.UUIDModel;
import org.kevoree.cloner.ModelCloner;
import org.kevoree.framework.KevoreePlatformHelper;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.library.sky.lxc.utils.FileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 05/06/13
 * Time: 09:34
 * To change this template use File | Settings | File Templates.
 */
public class LxcManager {


    private void updateNetworkProperties(ContainerRoot model, String remoteNodeName, String address) {
        System.out.println("set "+remoteNodeName+" "+address);
        KevoreePlatformHelper.instance$.updateNodeLinkProp(model, remoteNodeName, remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP(), address, "LAN", 100);
    }

    public boolean start(String id, String operating_system, LxcHostNode service, ContainerRoot iaasModel){
        try
        {
            System.out.println("Creating container " + id + " OS " + operating_system);

            String clone_target ="";
            if(operating_system.contains("ubuntu")){
                clone_target = "cloneubuntu";
            }    else    {
                clone_target = "cloneubuntu";
            }

            Process processcreate = new ProcessBuilder("lxc-clone","-o",clone_target,"-n",id).redirectErrorStream(true).start();
            FileManager.display_message_process(processcreate.getInputStream());
            processcreate.waitFor();

            System.out.println("Starting container " + id + " OS " + operating_system);
            Process lxcstartprocess = new ProcessBuilder("lxc-start","-n",id,"-d").start();
            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();

            String ip =null;
            do {
                ip = getIP(id) ;
                Thread.sleep(1000);
            } while (ip == null);

            System.out.println("Container is ready on "+ip);


            ModelCloner cloner = new ModelCloner();
            ContainerRoot readWriteModel = cloner.clone(iaasModel);
            updateNetworkProperties(readWriteModel,id,ip);
            service.getModelService().updateModel(readWriteModel);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;


    }


    public String getIP(String id)  {
        String line;
        try {
            Process processcreate = new ProcessBuilder("/bin/lxc-ip","-n",id).redirectErrorStream(true).start();
            BufferedReader input =  new BufferedReader(new InputStreamReader(processcreate.getInputStream()));
            line = input.readLine();
            input.close();
            return line;
        } catch (Exception e) {
            return  null;
        }

    }

    public boolean destroy(String id){
        try {
            System.out.println("Stoping container " + id );
            Process lxcstartprocess = new ProcessBuilder("lxc-stop","-n",id).redirectErrorStream(true).start();

            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        try {
            System.out.println("Destroying container " + id );
            Process lxcstartprocess = new ProcessBuilder("lxc-destroy","-n",id).redirectErrorStream(true).start();
            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

                             /*
            String path_to_copy_java = "/var/lib/lxc/"+nodeName+"/rootfs/opt";

            System.out.println("Copying jdk 1.7 "+path_to_copy_java);

            Process untarjava = new ProcessBuilder("/bin/cp","-R","/root/jdk",path_to_copy_java).start();

            FileManager.display_message_process(untarjava.getInputStream());

            String javahome = "echo \"JAVA_HOME=/opt/jdk\" >> /var/lib/lxc/"+nodeName+"/rootfs/etc/environment";
            String javabin = "echo \"PATH=$PATH:/opt/jdk/bin\"  >> /var/lib/lxc/"+nodeName+"/rootfs/etc/environment";
            Runtime.getRuntime().exec(javahome);
            Runtime.getRuntime().exec(javabin);                   System.out.println(javahome);
            System.out.println(javabin)
             */

}
