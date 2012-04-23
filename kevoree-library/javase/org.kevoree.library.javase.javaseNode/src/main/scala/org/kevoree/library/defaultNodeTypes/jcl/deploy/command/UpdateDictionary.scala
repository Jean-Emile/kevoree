package org.kevoree.library.defaultNodeTypes.jcl.deploy.command

/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import org.kevoree._
import api.PrimitiveCommand
import framework.osgi.{KevoreeInstanceActivator, KevoreeGroupActivator, KevoreeChannelFragmentActivator, KevoreeComponentActivator}
import library.defaultNodeTypes.jcl.deploy.context.{KevoreeMapping, KevoreeDeployManager}
import org.kevoree.framework.message.UpdateDictionaryMessage
import org.slf4j.LoggerFactory
import java.util.HashMap
import java.lang.String

case class UpdateDictionary(c: Instance, nodeName: String) extends PrimitiveCommand {

  var logger = LoggerFactory.getLogger(this.getClass)

  private var lastDictioanry: HashMap[String, AnyRef] = null


  def execute(): Boolean = {
    //BUILD MAP
    val dictionary: java.util.HashMap[String, AnyRef] = new java.util.HashMap[String, AnyRef]
    if (c.getTypeDefinition.getDictionaryType.isDefined) {
      if (c.getTypeDefinition.getDictionaryType.get.getDefaultValues != null) {
        c.getTypeDefinition.getDictionaryType.get.getDefaultValues.foreach {
          dv =>
            dictionary.put(dv.getAttribute.getName, dv.getValue)
        }
      }
    }

    if (c.getDictionary.isDefined) {
      c.getDictionary.get.getValues.foreach {
        v =>
          if (v.getAttribute.getFragmentDependant) {
            v.getTargetNode.map {
              tn =>
                if (tn.getName == nodeName) {
                  dictionary.put(v.getAttribute.getName, v.getValue)
                }
            }
          } else {
            dictionary.put(v.getAttribute.getName, v.getValue)
          }
      }
    }

    KevoreeDeployManager.bundleMapping.find(map => map.objClassName == c.getClass.getName && map.name == c.getName) match {
      case None => false
      case Some(mapfound) => {
        mapfound.asInstanceOf[KevoreeMapping].ref match {
          case iact: KevoreeInstanceActivator => {
            Thread.currentThread().setContextClassLoader(iact.getKInstance.getClass.getClassLoader)
            lastDictioanry = iact.getKInstance.kUpdateDictionary(dictionary, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])
            // lastDictioanry = (c_act.componentActor !? UpdateDictionaryMessage(dictionary, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
            lastDictioanry != null
          }
            /*
          case c_act: KevoreeChannelFragmentActivator => {
            lastDictioanry = (c_act.channelActor !? UpdateDictionaryMessage(dictionary, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
            true
          }
          case g_act: KevoreeGroupActivator => {
            lastDictioanry = (g_act.groupActor !? UpdateDictionaryMessage(dictionary, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
            true
          }   */
          case _ => false
        }
      }
    }

  }

  def undo() {
    KevoreeDeployManager.bundleMapping.find(map => map.objClassName == c.getClass.getName && map.name == c.getName) match {
      case None => //false
      case Some(mapfound) => {
        val tempHash = new HashMap[String, AnyRef]
        import scala.collection.JavaConversions._
        if (lastDictioanry != null) {
          lastDictioanry.foreach {
            dic =>
              tempHash.put(dic._1, dic._2.toString)
          }
          KevoreeDeployManager.bundleMapping.find(map => map.objClassName == c.getClass.getName && map.name == c.getName) match {
            case None =>
            case Some(mapfound) => {

              mapfound.asInstanceOf[KevoreeMapping].ref match {
                case iact: KevoreeInstanceActivator => {
                  Thread.currentThread().setContextClassLoader(iact.getKInstance.getClass.getClassLoader)
                  lastDictioanry = iact.getKInstance.kUpdateDictionary(tempHash, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])


                  //lastDictioanry = (c_act.componentActor !? UpdateDictionaryMessage(tempHash, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
                  //true
                }
                  /*
                case c_act: KevoreeChannelFragmentActivator => {
                  lastDictioanry = (c_act.channelActor !? UpdateDictionaryMessage(tempHash, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
                  //true
                }
                case g_act: KevoreeGroupActivator => {
                  lastDictioanry = (g_act.groupActor !? UpdateDictionaryMessage(tempHash, c.getTypeDefinition.eContainer.asInstanceOf[ContainerRoot])).asInstanceOf[HashMap[String, AnyRef]]
                  //true
                }*/

                case _ =>
              }
            }
          }
        }
      }
    }

  }

}
