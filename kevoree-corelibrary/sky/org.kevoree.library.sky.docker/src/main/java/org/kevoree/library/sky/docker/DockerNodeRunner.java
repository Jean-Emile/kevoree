package org.kevoree.library.sky.docker;

import org.kevoree.ContainerRoot;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 23/05/13
 * Time: 16:26
 */
public class DockerNodeRunner extends KevoreeNodeRunner {

    public DockerNodeRunner(String nodeName) {
        super(nodeName);
    }

    private String kevoreeBootStrap = "/bin/sh curl http://boot.kevoree.org";
    private String dockerStartCmd = "docker run -h=\"nodeName\" base " + kevoreeBootStrap;
    private String dockerStopCmd = "docker stop nodeName";

    @Override
    public boolean startNode(ContainerRoot iaasModel, ContainerRoot childBootStrapModel) {
        try {
            Process p = Runtime.getRuntime().exec(dockerStartCmd.replace("nodeName", nodeName()));
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                Log.info(s);
            }
            while ((s = stdError.readLine()) != null) {
                Log.error(s);
            }
            p.wait();
            if (p.exitValue() == 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.error("Error while starting child node");
            return false;
        }
    }

    @Override
    public boolean stopNode() {
        try {
            Process p = Runtime.getRuntime().exec(dockerStopCmd.replace("nodeName", nodeName()));
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                Log.info(s);
            }
            while ((s = stdError.readLine()) != null) {
                Log.error(s);
            }
            p.wait();
            if (p.exitValue() == 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.error("Error while stoping child node");
            return false;
        }
    }
}
