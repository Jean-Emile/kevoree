package org.kevoree.library.javase.webserver.webbit;

import org.kevoree.context.Metric;
import org.kevoree.context.PutHelper;
import org.kevoree.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 11/12/12
 * Time: 11:46
 */
public class SelfMonitor implements Runnable {

    private URL url = null;
    private Metric m = null;

    public SelfMonitor(URL url, Metric m) {
        this.url = url;
        this.m = m;
    }

    @Override
    public void run() {
        BufferedReader in = null;
        try {
            long before = System.currentTimeMillis();
            URLConnection sc = this.url.openConnection();
            in = new BufferedReader(new InputStreamReader(sc.getInputStream()));
            while (in.readLine() != null) {
                //IGNORE
            }
            in.close();
            //CHECK THE TIME
            long latency = System.currentTimeMillis() - before;
            PutHelper.addValue(m, latency + "");

            Log.info("Put latency (ms) = " + latency);

        } catch (IOException e) {
            Log.error("",e);
            //TODO put in context latency very high
        } finally {
            try {
                in.close();
            } catch (Exception e) {

            }
        }
    }
}
