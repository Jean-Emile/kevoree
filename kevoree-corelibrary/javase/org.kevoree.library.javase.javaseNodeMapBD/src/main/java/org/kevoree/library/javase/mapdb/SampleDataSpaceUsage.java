package org.kevoree.library.javase.mapdb;

import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Start;
import org.kevoree.api.dataspace.DataSpaceService;
import org.kevoree.framework.AbstractComponentType;

/**
 * Created by duke on 24/06/13.
 */
@ComponentType
public class SampleDataSpaceUsage extends AbstractComponentType {

    @KevoreeInject
    public DataSpaceService data;

    @Start
    public void start(){
        data.putData("/duke/context/hello","Hello World !");
        System.out.println(data.getData("/duke/context/hello"));
    }


}
