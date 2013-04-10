package org.kevoree.library.haproxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 10/04/13
 * Time: 10:15
 */
public class OutputReader extends Thread {

    private InputStream is = null;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Boolean error = false;

    public OutputReader(InputStream _is,Boolean _error) {
        is = _is;
        error = _error;
    }

    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            for (; ; ) {
                String s = br.readLine();
                if (s == null){
                    break;
                } else {
                   if(error){
                       logger.error(s);
                   } else {
                       logger.info(s);
                   }
                }
            }
        } catch (IOException ioe) {
        }
    }

}
