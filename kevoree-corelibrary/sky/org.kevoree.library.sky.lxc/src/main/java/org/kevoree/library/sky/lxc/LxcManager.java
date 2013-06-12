package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.api.service.core.handler.UUIDModel;
import org.kevoree.api.service.core.script.KevScriptEngine;
import org.kevoree.api.service.core.script.KevScriptEngineException;
import org.kevoree.api.service.core.script.KevScriptEngineFactory;
import org.kevoree.cloner.ModelCloner;
import org.kevoree.framework.KevoreePlatformHelper;
import org.kevoree.framework.KevoreePropertyHelper;
import org.kevoree.impl.DefaultKevoreeFactory;
import org.kevoree.library.sky.lxc.utils.FileManager;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 05/06/13
 * Time: 09:34
 * To change this template use File | Settings | File Templates.
 */
public class LxcManager {

    private  String clone_id = "";
    private final int timeout = 50;
    public void setClone_id(String id){
        this.clone_id = id;
    }

    private void updateNetworkProperties(ContainerRoot model, String remoteNodeName, String address) {
        Log.debug("set "+remoteNodeName+" "+address);
        KevoreePlatformHelper.instance$.updateNodeLinkProp(model, remoteNodeName, remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP(), address, "LAN", 100);
    }


    public void lxc_start(String id) throws InterruptedException, IOException {

        Log.debug("Starting container " + id);
        Process lxcstartprocess = new ProcessBuilder("lxc-start","-n",id,"-d").start();
        FileManager.display_message_process(lxcstartprocess.getInputStream());
        lxcstartprocess.waitFor();
    }
    public boolean start(String id, String id_clone, LxcHostNode service, ContainerRoot iaasModel){
        try
        {
            Log.debug("LxcManager : "+id+" clone =>"+id_clone);

            if(!getNodes().contains(id))
            {
                Log.debug("Creating container " + id + " OS " + id_clone);
                Process processcreate = new ProcessBuilder("lxc-clone","-o",id_clone,"-n",id).redirectErrorStream(true).start();
                FileManager.display_message_process(processcreate.getInputStream());
                processcreate.waitFor();
            } else
            {
                Log.warn("Container {} already exist",iaasModel);
            }

            lxc_start(id);

            String ip =null;
            int c = 0;
            do {
                ip = getIP(id) ;
                Thread.sleep(1000);
                c++;
            } while (ip == null && c < timeout);

            Log.debug("Container is ready on "+ip);

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

    public  List<String> getNodes()   {
        List<String> containers = new ArrayList<String>();
        Process processcreate = null;
        try {
            processcreate = new ProcessBuilder("/bin/lxc-list-containers").redirectErrorStream(true).start();

            BufferedReader input =  new BufferedReader(new InputStreamReader(processcreate.getInputStream()));
            String line;
            while ((line = input.readLine()) != null){
                if(!line.equals(clone_id)){
                    containers.add(line);
                }
            }
            input.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return containers;
    }


    public  ContainerRoot buildModelCurrentLxcState(KevScriptEngineFactory factory,String nodename) throws IOException, KevScriptEngineException {

        DefaultKevoreeFactory defaultKevoreeFactory = new DefaultKevoreeFactory();
        KevScriptEngine  engine = factory.createKevScriptEngine();
        if(getNodes().size() > 0){

            Log.debug("ADD => "+getNodes()+" in current model");

            engine.append("merge 'mvn:org.kevoree.corelibrary.sky/org.kevoree.library.sky.lxc/"+defaultKevoreeFactory.getVersion()+"'");
            engine.append("addNode "+nodename+":LxcHostNode");

            for(String node_child_id :getNodes()){
                engine.append("addNode "+node_child_id+":LxcHostNode");
                engine.append("updateDictionary "+node_child_id+"{log_folder=\"/tmp\",role=\"host\"}");
                engine.append("addChild "+node_child_id+"@"+nodename);

                //    String ip = LxcManager.getIP(node_child_id);
            }

            return   engine.interpret();


        }
        return    defaultKevoreeFactory.createContainerRoot();
    }

    public static  String getIP(String id)  {
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

    public boolean stop(String id,boolean destroy){
        try {
            Log.debug("Stoping container " + id );
            Process lxcstartprocess = new ProcessBuilder("lxc-stop","-n",id).redirectErrorStream(true).start();

            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if(destroy){
            try {
                Log.debug("Destroying container " + id );
                Process lxcstartprocess = new ProcessBuilder("lxc-stop","-n",id).redirectErrorStream(true).start();
                FileManager.display_message_process(lxcstartprocess.getInputStream());
                lxcstartprocess.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
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
