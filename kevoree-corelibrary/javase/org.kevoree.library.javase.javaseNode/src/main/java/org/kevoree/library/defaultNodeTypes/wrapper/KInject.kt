package org.kevoree.library.defaultNodeTypes.wrapper

import org.kevoree.library.defaultNodeTypes.reflect.FieldAnnotationResolver
import org.kevoree.annotation.KevoreeInject
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.Bootstraper
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.HashMap
import org.kevoree.api.dataspace.DataSpaceService

/**
 * Created by duke on 24/06/13.
 */
public class KInject(val instance: Any, val modelService: KevoreeModelHandlerService, val bootService: Bootstraper, val kevsEngine: KevScriptEngineFactory, val dataSpace: DataSpaceService?) {

    private val fieldResolver = FieldAnnotationResolver(instance.javaClass);
    public val clazzList: HashMap<java.lang.Class<out Any>, Any> = HashMap<java.lang.Class<out Any>, Any>();

    {
        clazzList.put(javaClass<KevoreeModelHandlerService>(), modelService)
        clazzList.put(javaClass<Bootstraper>(), bootService)
        clazzList.put(javaClass<KevScriptEngineFactory>(), kevsEngine)

        if(dataSpace != null){
            clazzList.put(javaClass<DataSpaceService>(), dataSpace)
        }
    }

    public fun kinject() {

        for(clazz in clazzList.keySet()){
            val modelServiceFields = fieldResolver.resolve(javaClass<KevoreeInject>(), clazz)!!
            for(mserv in modelServiceFields){
                if(Modifier.isPrivate(mserv.getModifiers())){
                    mserv.setAccessible(true);
                }
                mserv.set(instance, clazzList.get(clazz))
            }
        }

    }


}