package org.kevoree.library.monitored;

import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 29/04/13
 * Time: 18:26
 */
@Library(name = "JavaSE")
@ComponentType
@DictionaryType({
        @DictionaryAttribute(name = "period", defaultValue = "5000", optional = true)
})
public class ThreadGroupStatsPrinter extends AbstractComponentType implements Runnable {
    @Override
    public void run() {
        try {
            for (String key : KevoreeDeployManager.instance$.getInternalMap().keySet()) {
                if (key.contains("_tg")) {
                    ThreadGroup tg = (ThreadGroup) KevoreeDeployManager.instance$.getInternalMap().get(key);
                    Thread[] lists = new Thread[tg.activeCount()];
                    tg.enumerate(lists);
                    for(Thread t : lists){
                        System.out.println("TG_CPU% "+tg.getName()+" : "+tbean.getThreadCpuTime(t.getId()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private ScheduledExecutorService t = null;
    private ThreadMXBean tbean = null;

    @Start
    public void start() throws IOException {
        tbean = ManagementFactory.getThreadMXBean();
        t = Executors.newScheduledThreadPool(1);
        t.scheduleAtFixedRate(this, 0, Integer.parseInt(getDictionary().get("period").toString()), TimeUnit.MILLISECONDS);
    }

    @Stop
    public void stop() {
        t.shutdownNow();
        tbean = null;
    }

}
