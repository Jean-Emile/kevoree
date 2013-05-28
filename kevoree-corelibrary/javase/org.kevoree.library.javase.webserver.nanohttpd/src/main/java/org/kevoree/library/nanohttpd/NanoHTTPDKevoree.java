package org.kevoree.library.nanohttpd;

import org.kevoree.framework.MessagePort;
import org.kevoree.library.javase.webserver.KevoreeHttpRequest;
import org.kevoree.library.javase.webserver.KevoreeHttpResponse;
import org.kevoree.library.javase.webserver.impl.KevoreeHttpRequestImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An example of subclassing NanoHTTPD to make a custom HTTP server.
 */
public class NanoHTTPDKevoree extends NanoHTTPD {

    private  MessagePort handler;
    private  BlockingConcurrentHashMap<Integer,KevoreeHttpResponse> pool = new BlockingConcurrentHashMap();
    private Random rand = new Random();

    public NanoHTTPDKevoree(MessagePort h, int port) {
        super(port);
        this.handler = h;
    }

    @Override
    public Response serve(String uri, Method method, Map<String, String> header, Map<String, String> parms, Map<String, String> files) {
      //  System.out.println(method + " '" + uri + "' ");

        KevoreeHttpRequest request = new KevoreeHttpRequestImpl();
        request.setMethod(method.name());
        request.setTokenID(rand.nextInt());
        request.setUrl(uri);

        request.setHeaders(header);
        request.setResolvedParams(parms);
        request.setRawParams(parms.toString());

        handler.process(request);

        try {
            // wait reponse
            KevoreeHttpResponse response =   pool.getAndWait(request.getTokenID());
            return new NanoHTTPD.Response(response.getContent());
        } catch (InterruptedException e) {
            String msg = "<html><body><h1>Timeout server</h1>\n";
            msg += "</body></html>\n";

            return new NanoHTTPD.Response(msg);

        }

    }

    public static void main(String[] args) {
        ServerRunner.run(NanoHTTPDKevoree.class);
    }

    public void responseHandler(Object param) {
        if(param instanceof KevoreeHttpResponse){
            KevoreeHttpResponse r = (KevoreeHttpResponse)param;
            pool.put(r.getTokenID(),r);
        }
    }
}
