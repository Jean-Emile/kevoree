package org.kevoree.library.sky.lxc;

import org.kevoree.ContainerRoot;
import org.kevoree.api.service.core.handler.UUIDModel;
import org.kevoree.api.service.core.script.KevScriptEngine;
import org.kevoree.api.service.core.script.KevScriptEngineException;
import org.kevoree.api.service.core.script.KevScriptEngineFactory;
import org.kevoree.cloner.ModelCloner;
import org.kevoree.framework.KevoreePlatformHelper;
import org.kevoree.impl.DefaultKevoreeFactory;
import org.kevoree.library.sky.lxc.utils.FileManager;
import org.kevoree.library.sky.lxc.utils.SystemHelper;
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

    private String clone_id = "baseclonekevore";
    private final int timeout = 50;

    private final String lxcstart = "lxc-start";
    private final String lxcstop = "lxc-stop";
    private final String lxcdestroy = "lxc-destroy";
    private final String lxcshutdown = "lxc-shutdown";
    private final String lxcclone = "lxc-clone";
    private final String lxccreate = "lxc-create";


    public boolean create_container(String id, String id_clone, LxcHostNode service, ContainerRoot iaasModel) {
        try {
            Log.debug("LxcManager : " + id + " clone =>" + id_clone);

            if (!getContainers().contains(id)) {
                Log.debug("Creating container " + id + " OS " + id_clone);
                Process processcreate = new ProcessBuilder(lxcclone, "-o", id_clone, "-n", id).redirectErrorStream(true).start();
                FileManager.display_message_process(processcreate.getInputStream());
                processcreate.waitFor();
            } else {
                Log.warn("Container {} already exist", iaasModel);
            }
        } catch (Exception e) {
            Log.error("create_container", e);
            return false;
        }
        return true;
    }

    public boolean start_container(String id, LxcHostNode service, ContainerRoot iaasModel) {

        try {
            UUIDModel uuidModel = service.getModelService().getLastUUIDModel();
            lxc_start_container(id);

            String ip = null;
            int c = 0;
            do {
                ip = getIP(id);
                Thread.sleep(500);
                c++;
            } while (ip == null && c < timeout);

            if (ip == null) {
                Log.error("When all attempts to get the address IP have failed");
                return false;
            } else {

                Log.debug("Container is ready on " + ip);

                ModelCloner cloner = new ModelCloner();
                ContainerRoot readWriteModel = cloner.clone(iaasModel);
                updateNetworkProperties(readWriteModel, id, ip);

                service.getModelService().compareAndSwapModel(uuidModel, readWriteModel);
                return true;
            }
        } catch (InterruptedException e) {
            Log.error("start_container", e);
            return false;
        } catch (IOException e) {
            Log.error("start_container", e);
            return false;
        }
    }


    private void lxc_start_container(String id) throws InterruptedException, IOException {
        Log.debug("Starting container " + id);
        Process lxcstartprocess = new ProcessBuilder(lxcstart, "-n", id, "-d").start();
        FileManager.display_message_process(lxcstartprocess.getInputStream());
        lxcstartprocess.waitFor();
    }

    public List<String> getContainers() {
        List<String> containers = new ArrayList<String>();
        Process processcreate = null;
        try {
            processcreate = new ProcessBuilder("/bin/lxc-list-containers").redirectErrorStream(true).start();

            BufferedReader input = new BufferedReader(new InputStreamReader(processcreate.getInputStream()));
            String line;
            while ((line = input.readLine()) != null) {
                containers.add(line);
            }
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return containers;
    }


    public ContainerRoot buildModelCurrentLxcState(KevScriptEngineFactory factory, String nodename) throws IOException, KevScriptEngineException {

        DefaultKevoreeFactory defaultKevoreeFactory = new DefaultKevoreeFactory();
        KevScriptEngine engine = factory.createKevScriptEngine();
        if (getContainers().size() > 0) {

            Log.debug("ADD => " + getContainers() + " in current model");

            engine.append("merge 'mvn:org.kevoree.corelibrary.sky/org.kevoree.library.sky.lxc/" + defaultKevoreeFactory.getVersion() + "'");
            engine.append("addNode " + nodename + ":LxcHostNode");

            for (String node_child_id : getContainers()) {
                if (!node_child_id.equals(clone_id)) {
                    engine.append("addNode " + node_child_id + ":LxcHostNode");
                    engine.append("updateDictionary " + node_child_id + "{log_folder=\"/tmp\",role=\"host\"}");
                    engine.append("addChild " + node_child_id + "@" + nodename);
                }
                //    String ip = LxcManager.getIP(node_child_id);
            }

            return engine.interpret();


        }
        return defaultKevoreeFactory.createContainerRoot();
    }

    public static String getIP(String id) {
        String line;
        try {
            Process processcreate = new ProcessBuilder("/bin/lxc-ip", "-n", id).redirectErrorStream(true).start();
            BufferedReader input = new BufferedReader(new InputStreamReader(processcreate.getInputStream()));
            line = input.readLine();
            input.close();
            return line;
        } catch (Exception e) {
            return null;
        }

    }

    private boolean lxc_stop_container(String id, boolean destroy) {
        try {
            Log.debug("Stoping container " + id);
            Process lxcstartprocess = new ProcessBuilder(lxcstop, "-n", id).redirectErrorStream(true).start();

            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();
        } catch (Exception e) {
            Log.error("lxc_stop_container ", e);
            return false;
        }
        if (destroy) {
            try {
                Log.debug("Destroying container " + id);
                Process lxcstartprocess = new ProcessBuilder(lxcdestroy, "-n", id).redirectErrorStream(true).start();
                FileManager.display_message_process(lxcstartprocess.getInputStream());
                lxcstartprocess.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }


        return true;
    }

    public boolean stop_container(String id) {
        return lxc_stop_container(id, false);
    }

    public boolean remove_container(String id) {
        return lxc_stop_container(id, true);
    }

    public void createClone() throws IOException, InterruptedException {
        if (!getContainers().contains(clone_id)) {
            Log.debug("Creating the clone");
            Process lxcstartprocess = new ProcessBuilder(lxccreate, "-n", clone_id, "-t", "kevoree").redirectErrorStream(true).start();
            FileManager.display_message_process(lxcstartprocess.getInputStream());
            lxcstartprocess.waitFor();
        }
    }


    private void updateNetworkProperties(ContainerRoot model, String remoteNodeName, String address) {
        Log.debug("set " + remoteNodeName + " " + address);
        KevoreePlatformHelper.instance$.updateNodeLinkProp(model, remoteNodeName, remoteNodeName, org.kevoree.framework.Constants.instance$.getKEVOREE_PLATFORM_REMOTE_NODE_IP(), address, "LAN", 100);
    }

    public void copy(String file, String path) throws IOException {
        FileManager.copyFileFromStream(LxcManager.class.getClassLoader().getResourceAsStream(file), path, file, true);
    }

    public void allow_exec(String path_file_exec) throws IOException {
        if (SystemHelper.getOS() != SystemHelper.OS.WIN32 && SystemHelper.getOS() != SystemHelper.OS.WIN64) {
            System.out.println("chmod +x " + path_file_exec);
            // todo check os
            Runtime.getRuntime().exec("chmod 777 " + path_file_exec);
        } else {
            // win32
            System.err.println("ERROR");
        }
    }

    /**
     * Install scripts and template
     *
     * @throws IOException
     */
    public void install() throws IOException {
        copy("lxc-ip", "/bin");
        allow_exec("/bin/lxc-ip");
        copy("lxc-list-containers", "/bin");
        allow_exec("/bin/lxc-list-containers");
        copy("lxc-kevoree", "/usr/share/lxc/templates");
        allow_exec("/usr/share/lxc/templates/lxc-kevoree");
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
