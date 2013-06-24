package org.kevoree.library.monitored;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import javolution.util.FastMap;
import org.kevoree.Instance;
import org.kevoree.annotation.Library;
import org.kevoree.annotation.NodeType;
import org.kevoree.api.PrimitiveCommand;
import org.kevoree.kompare.JavaSePrimitive;
import org.kevoree.library.defaultNodeTypes.JavaSENode;
import org.kevoreeadaptation.AdaptationPrimitive;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 22/05/12
 * Time: 16:29
 */

@NodeType
@Library(name = "JavaSE")
public class MonitoredJavaSENode extends JavaSENode {

    private Map<String,Meter> gauges = null;

    @Override
    public void startNode() {
        gauges = new FastMap<String,Meter>().shared();
        super.startNode();
    }

    @Override
    public void stopNode() {
        super.stopNode();
        try {
            Metrics.shutdown();
        } catch(Exception ignore){
        }
        gauges = null;
    }

    public PrimitiveCommand getSuperPrimitive(AdaptationPrimitive a){
         return super.getPrimitive(a);
    }

    @Override
    public PrimitiveCommand getPrimitive(AdaptationPrimitive adaptationPrimitive) {

        if(adaptationPrimitive.getPrimitiveType().getName().equals(JavaSePrimitive.instance$.getAddInstance())){
               return new MonitoredAddInstance( (Instance)adaptationPrimitive.getRef(), getNodeName(), getModelService(), getKevScriptEngineFactory(), getBootStrapperService(),this);
        } else {
            return super.getPrimitive(adaptationPrimitive);
        }
    }


}

