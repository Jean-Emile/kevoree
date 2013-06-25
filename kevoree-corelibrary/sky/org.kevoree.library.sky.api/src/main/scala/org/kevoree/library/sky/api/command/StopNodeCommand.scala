package org.kevoree.library.sky.api.command

import org.kevoree.library.sky.api.KevoreeNodeManager
import org.kevoree.ContainerRoot
import org.kevoree.api.PrimitiveCommand

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 25/06/13
 * Time: 12:02
 *
 * @author Erwan Daubert
 * @version 1.0
 */
class StopNodeCommand(iaasModel: ContainerRoot,targetChildName : String,nodeManager : KevoreeNodeManager) extends PrimitiveCommand {
  def execute () : Boolean = {
    //TODO PRUNE MODEL

    nodeManager.stopNode(iaasModel,targetChildName)
  }

  def undo () {
    nodeManager.startNode(iaasModel,targetChildName, iaasModel)
  }

  override def toString:String ={
    "AddNode " + targetChildName
  }
}
