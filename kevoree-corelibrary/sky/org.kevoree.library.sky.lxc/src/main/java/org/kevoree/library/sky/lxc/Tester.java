package org.kevoree.library.sky.lxc;
 /*
import org.kevoree.ContainerRoot;
import org.kevoree.KevoreeFactory;
import org.kevoree.api.service.core.script.KevScriptEngineException;
import org.kevoree.framework.KevoreeXmiHelper;
import org.kevoree.impl.DefaultKevoreeFactory;
import org.kevoree.library.sky.api.PlanningManager;
import org.kevoree.tools.marShell.KevScriptOfflineEngine;
import org.kevoree.tools.modelsync.FakeBootstraperService;
import org.kevoreeadaptation.AdaptationModel;
import org.kevoreeadaptation.AdaptationPrimitive;

import java.util.HashMap;
 */
/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 07/06/13
 * Time: 11:41
 * To change this template use File | Settings | File Templates.
 */
public class Tester {


    public static void main(String argv[])  {
          /*
        LxcHostNode node = new LxcHostNode();

        node.setNodeName("node0");
        HashMap<String, Object> dico = new HashMap<String, Object>();
        dico.put("role", "host");
        dico.put("host", "localhost");
        dico.put("logLevel","DEBUG");
        dico.put("inet","127.0.0.1");
        dico.put("subnet","");
        dico.put("alias_mask","");
        dico.put("log_folder","/tmp") ;
        dico.put("coreLogLevel","DEBUG");
        dico.put("OS","");
        node.setDictionary(dico);




        ContainerRoot model =  KevoreeXmiHelper.instance$.loadStream(Tester.class.getClassLoader().getResourceAsStream("Model"));
       KevoreeFactory kevoreeFactory = new DefaultKevoreeFactory();
        ContainerRoot basemodel = kevoreeFactory.createContainerRoot();
        FakeBootstraperService bootstraper = new FakeBootstraperService();
        KevScriptOfflineEngine kevOfflineEngine = new KevScriptOfflineEngine(basemodel,bootstraper.getBootstrap()) ;
        for( String pro : System.getProperties().stringPropertyNames())
        {
            kevOfflineEngine.addVariable(pro,System.getProperty(pro));
        }
        kevOfflineEngine.addVariable("kevoree.version", kevoreeFactory.getVersion());

        String kevScript = "{merge \"mvn:org.kevoree.corelibrary.sky/org.kevoree.library.sky.lxc/"+kevoreeFactory.getVersion()+"\"\n" +
                "addNode node0:LxcHostNode\n" +
                "addNode uuu:LxcHostNode\n" +
                "addChild uuu@node0\n" +
                "}\n";
        // add MessagePort
        //  String kev_framework ="merge 'mvn:org.kevoree.corelibrary.android/org.kevoree.library.android.logger/{kevoree.version}'";

        kevOfflineEngine.append("{"+kevScript.replace("tblock","")+"}") ;

        ContainerRoot model2 = kevOfflineEngine.interpret();



        AdaptationModel adaptationModel = PlanningManager.kompare(model, model2, node);

        for(AdaptationPrimitive p : adaptationModel.getAdaptations())
        {
            System.out.println(p.getPrimitiveType()) ;
        }
              System.exit(0);      */
    }


}
