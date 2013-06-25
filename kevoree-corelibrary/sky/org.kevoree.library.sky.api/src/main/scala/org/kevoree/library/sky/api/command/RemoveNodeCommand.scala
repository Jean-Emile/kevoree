package org.kevoree.library.sky.api.command

import org.kevoree.ContainerRoot
import org.kevoree.api.PrimitiveCommand
import org.kevoree.library.sky.api.KevoreeNodeManager

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 22/09/11
 * Time: 11:40
 *
 * @author Erwan Daubert
 * @version 1.0
 */

class RemoveNodeCommand (iaasModel: ContainerRoot,targetChildName : String,nodeManager : KevoreeNodeManager)
  extends PrimitiveCommand {

  override def execute (): Boolean = {
    nodeManager.removeNode(iaasModel,targetChildName)
  }

  override def undo () {
    nodeManager.addNode(iaasModel,targetChildName,iaasModel)
  }

  override def toString:String ={
    "RemoveNode " + targetChildName
  }
}