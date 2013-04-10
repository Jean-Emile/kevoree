package org.kevoree.library;

import org.kevoree.ContainerRoot;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 10/04/13
 * Time: 14:26
 * To change this template use File | Settings | File Templates.
 */
public class Benchmark {

    public static void main(String[] args) throws Exception {

        BasicGroup group = new BasicGroup();


        HashMap<String, Object> dico = new HashMap<String, Object>();
        dico.put("port", "8080");
        dico.put("ip","localhost");

        group.setDictionary(dico);

        ExecutorService s = Executors.newFixedThreadPool(10);

        ContainerRoot modeltarget = KevScriptLoader.getModel(Tester.class.getClassLoader().getResource("randomcomponent500.kevs").getPath());
        ContainerRoot modelempty = KevScriptLoader.getModel(Tester.class.getClassLoader().getResource("emptynode.kevs").getPath());



        for(int i=0;i<500;i++)
        {
            System.out.println("PUSH MODEL "+i);
            group.push(modeltarget, "node0");

            Thread.sleep(400);
            System.out.println("PUSH  EMPTY MODEL "+i);
            group.push(modelempty, "node0");
        }
    }
}
