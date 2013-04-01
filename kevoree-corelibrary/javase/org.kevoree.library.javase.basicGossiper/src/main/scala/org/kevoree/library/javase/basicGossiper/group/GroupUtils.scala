package org.kevoree.library.javase.basicGossiper.group

import org.kevoree.ContainerRoot
import scala.collection.JavaConversions._
import org.kevoree.framework.kaspects.TypeDefinitionAspect

object GroupUtils {

  private val typeDefinitionAspect = new TypeDefinitionAspect()

  def detectHaraKiri(newModel:ContainerRoot,oldModel:ContainerRoot,instanceGroupName:String,nodeName:String) : Boolean={
    //SEARCH FOR NEW GROUP INSTANCE IN NEW MODEL
    newModel.getGroups.find(group => group.getName==instanceGroupName && group.getSubNodes.exists(sub=>sub.getName == nodeName)) match {
      case Some(newGroup)=> {
          //SEARCH FOR ACTUAL GROUP
          oldModel.getGroups.find(group => group.getName==instanceGroupName && group.getSubNodes.exists(sub=>sub.getName == nodeName)) match {
            case Some(currentGroup)=> {
                //TYPE DEF HASHCODE COMPARE
                val node = newModel.getNodes.find(node=>node.getName==nodeName).get
                
                
                typeDefinitionAspect.foundRelevantDeployUnit(newGroup.getTypeDefinition, node).getHashcode != typeDefinitionAspect.foundRelevantDeployUnit(currentGroup.getTypeDefinition, node).getHashcode
            }
            case None => true//STRANGE ERROR wTf  - HaraKiri best effort
          }
      }
      case None => true //INSTANCE WILL BE UNINSTALL
    }
  }
  
}
