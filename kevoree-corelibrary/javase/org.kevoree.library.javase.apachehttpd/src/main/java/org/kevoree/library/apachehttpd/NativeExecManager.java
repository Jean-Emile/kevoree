package org.kevoree.library.apachehttpd;

import org.kevoree.library.apachehttpd.utils.FileManager;
import org.kevoree.library.apachehttpd.utils.SystemHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 15/05/13
 * Time: 11:12
 * To change this template use File | Settings | File Templates.
 */
public class NativeExecManager {


    private Properties properties = new Properties();
    private String httpdexecpath ="";
    private String httpdconfigpath = "";
    private  String apache_directory =  "";
    private String version = "";


    public NativeExecManager(){


        properties.put("PidFile","httpd.pid");
        properties.put("Listen","8080");
        properties.put("ServerName","localhost");
        properties.put("ServerTokens","Prod");
        properties.put("ServerSignature","Off");
        properties.put("FileETag","None");
        properties.put("UseCanonicalName","Off");
        properties.put("HostnameLookups","Off");
        properties.put("AddDefaultCharset","UTF-8");
        properties.put("ServerAdmin","webadmin@localhost");
        properties.put("ServerRoot", "/tmp");
        properties.put("DocumentRoot", "/tmp");
        properties.put("Timeout", "45");
        properties.put("KeepAlive", "On");
        properties.put("KeepAliveTimeout", "15");
        properties.put("MaxKeepAliveRequests", "100");
        properties.put("MaxRequestsPerChild", "1000");
        properties.put("ErrorLog", "error.log");
        properties.put("LogLevel", "error");
        properties.put("DefaultType", "text/plain");
        properties.put("LogLevel", "error");

    }

    public void setNameNativeExec(String name){
        this.version = name;
        apache_directory =System.getProperty("java.io.tmpdir")+ File.separatorChar+name;
    }

    public void setPort(int port){
        properties.setProperty("Listen", "" + port);
    }

    public void setDocumentRoot(String root){
        File checker = new File(root);
        if(checker.isDirectory()){
            properties.setProperty("DocumentRoot",root);
        }  else {
            System.err.println("setDocumentRoot failure is not a directory");
        }
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void updateConfiguration() throws IOException {
        StringBuilder data = new StringBuilder();
        for(Object key : properties.keySet()){
            String append = key+" "+properties.getProperty(key.toString())+" \n";
            data.append(append);
        }
        httpdconfigpath = apache_directory+""+File.separatorChar+"httpd.conf";
        FileManager.writeFile(httpdconfigpath,data.toString(),false);
        System.out.println("Updating "+httpdconfigpath);
    }


    public void allow_exec(String path_file_exec) throws IOException {
        if(SystemHelper.getOS() != SystemHelper.OS.WIN32 && SystemHelper.getOS() != SystemHelper.OS.WIN64){
            System.out.println("chmod +x "+path_file_exec);
            // todo check os
            Runtime.getRuntime().exec("chmod 777 "+path_file_exec);
        } else {
            // win32
            System.err.println("ERROR");
        }
    }
    public  void install_generics(){
        File tmpd = new File(apache_directory+File.separatorChar+"conf");
        tmpd.mkdirs();

        properties.setProperty("ServerRoot",apache_directory);
        try
        {
            httpdexecpath =install_lib("httpd");

            allow_exec(httpdexecpath);

            FileManager.copyFileFromPath(SystemHelper.getPathOS()+version+File.separatorChar+"mime.types", apache_directory+File.separatorChar+"conf", "mime.types");
            updateConfiguration();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String install_lib(String name) throws IOException {
        System.out.println("Copying "+SystemHelper.getPathOS()+version+File.separatorChar+name+"  -->  "+apache_directory);
        return FileManager.copyFileFromPath(SystemHelper.getPathOS()+version+File.separatorChar+name, apache_directory, name);
    }


    public  void start() throws IOException, InterruptedException {
        String line;
        String[] params = new String [3];
        params[0] = httpdexecpath;
        params[1] = "-f";
        params[2] = httpdconfigpath;


        ProcessBuilder pb = new ProcessBuilder(httpdexecpath,"-f",httpdconfigpath,"-k","start");
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH",apache_directory);

        Process javap = pb.start();


       // TODO create thread
        BufferedReader input =
                new BufferedReader
                        (new InputStreamReader(javap.getInputStream()));

        while ((line = input.readLine()) != null)
            System.out.println(line);

        javap.waitFor();
        input.close();
    }


    public void stop() throws IOException, InterruptedException {
        String line;
        String[] params = new String [3];
        params[0] = httpdexecpath;
        params[1] = "-f";
        params[2] = httpdconfigpath;


        ProcessBuilder pb = new ProcessBuilder(httpdexecpath,"-f",httpdconfigpath,"-k","stop");
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH",apache_directory);

        Process javap = pb.start();

        BufferedReader input =
                new BufferedReader
                        (new InputStreamReader(javap.getInputStream()));

        while ((line = input.readLine()) != null)
            System.out.println(line);

        javap.waitFor();
        input.close();


    }

    public void restart() throws IOException, InterruptedException {
        String line;
        String[] params = new String [3];
        params[0] = httpdexecpath;
        params[1] = "-f";
        params[2] = httpdconfigpath;


        ProcessBuilder pb = new ProcessBuilder(httpdexecpath,"-f",httpdconfigpath,"-k","restart");
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH",apache_directory);

        Process javap = pb.start();

        BufferedReader input =
                new BufferedReader
                        (new InputStreamReader(javap.getInputStream()));

        while ((line = input.readLine()) != null)
            System.out.println(line);

        javap.waitFor();
        input.close();


    }

}
