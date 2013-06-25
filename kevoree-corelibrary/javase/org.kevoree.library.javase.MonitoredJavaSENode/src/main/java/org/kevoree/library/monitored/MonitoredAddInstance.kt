package org.kevoree.library.monitored

import org.kevoree.library.defaultNodeTypes.command.AddInstance
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.Instance
import org.kevoree.api.PrimitiveCommand
import org.slf4j.LoggerFactory
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager
import org.kevoree.framework.AbstractNodeType

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 2/1/13
 * Time: 2:20 PM
 */


class MonitoredAddInstance(val c: Instance, nodeName: String,modelservice : KevoreeModelHandlerService,kscript : KevScriptEngineFactory,bs : org.kevoree.api.Bootstraper, nt : AbstractNodeType) : PrimitiveCommand {
    override fun execute(): Boolean {
        if(embedCmd.execute()){
            val ref = KevoreeDeployManager.getRef(c.javaClass.getName(),c.getName())
            return true
        } else {
            return false
        }
    }
    override fun undo() {
        embedCmd.undo()
    }

    val embedCmd = AddInstance(c,nodeName,modelservice,kscript,bs,nt)
    val logger = LoggerFactory.getLogger(this.javaClass)!!


}
