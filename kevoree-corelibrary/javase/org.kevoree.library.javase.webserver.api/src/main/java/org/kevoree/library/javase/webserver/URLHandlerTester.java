package org.kevoree.library.javase.webserver;

import org.kevoree.library.javase.webserver.impl.KevoreeHttpRequestImpl;

import java.util.Map;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 06/05/13
 * Time: 15:00
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class URLHandlerTester {

    public static void main(String[] args) {
        URLHandler h = new URLHandler();

        System.out.println(h.getLastParam("/core/t", "/{p1}/**"));
        System.out.println(h.getLastParam("/core/ttoto", "/{p1}/**"));

        KevoreeHttpRequestImpl url = new KevoreeHttpRequestImpl();
        url.setUrl("/core/toto/tutu");

        h.initRegex("/{base}/{name}/{id}");

        Map<String, String> params =  h.check(url).getResolvedParams();
        for (String key :params.keySet()) {
            System.out.println(key + " = " + params.get(key));
        }
    }
}
