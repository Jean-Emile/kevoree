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

class AddNodeCommand(iaasModel: ContainerRoot,targetChildName : String,nodeManager : KevoreeNodeManager) extends PrimitiveCommand {
  def execute () : Boolean = {
    //TODO PRUNE MODEL

    nodeManager.addNode(iaasModel,targetChildName,iaasModel)
  }

  def undo () {
    nodeManager.removeNode(iaasModel,targetChildName)
  }

  override def toString:String ={
    "AddNode " + targetChildName
  }
}