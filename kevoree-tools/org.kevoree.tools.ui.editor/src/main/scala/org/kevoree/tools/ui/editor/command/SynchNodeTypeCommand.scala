/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kevoree.tools.ui.editor.command

import reflect.BeanProperty
import org.kevoree.tools.ui.editor.{EmbeddedOSGiEnv, CommandHelper, KevoreeUIKernel}
import scala.collection.JavaConversions._
import org.kevoree.{NodeType, DeployUnit, ContainerRoot}
import org.osgi.framework.{Bundle, BundleException}
import org.kevoree.framework.AbstractNodeType

class SynchNodeTypeCommand extends Command {

  @BeanProperty
  var kernel: KevoreeUIKernel = null

  @BeanProperty
  var destNodeName: String = null

  var bundle: Bundle = null


  def execute(p: AnyRef) {

    try {

      val model: ContainerRoot = kernel.getModelHandler.getActualModel
      //LOCATE NODE
      val node = model.getNodes.find(node => node.getName == destNodeName)
      node match {
        case Some(node) => {
          val nodeTypeDeployUnitList = node.getTypeDefinition.getDeployUnits.toList
          if (nodeTypeDeployUnitList.size > 0) {
            println("nodeType installation => " + installNodeTyp(nodeTypeDeployUnitList.get(0)))

            val clazz: Class[_] = bundle.loadClass(node.getTypeDefinition.getBean)
            val nodeType = clazz.newInstance.asInstanceOf[AbstractNodeType]

            //ADD INSTANCE DICTIONARY
            val dictionary: java.util.HashMap[String, AnyRef] = new java.util.HashMap[String, AnyRef]
            if (node.getTypeDefinition.getDictionaryType != null) {
              if (node.getTypeDefinition.getDictionaryType.getDefaultValues != null) {
                node.getTypeDefinition.getDictionaryType.getDefaultValues.foreach {
                  dv =>
                    dictionary.put(dv.getAttribute.getName, dv.getValue)
                }
              }
            }
            if (node.getDictionary != null) {
              node.getDictionary.getValues.foreach {
                v =>
                  dictionary.put(v.getAttribute.getName, v.getValue)
              }
            }

            nodeType.setDictionary(dictionary)


            nodeType.push(destNodeName, model, bundle.getBundleContext)

          } else {
            println("NodeType deploy unit not found , have you forgotten to merge nodetype library ?")
          }
        }
        case None => println("NodeType not found for name " + destNodeName)
      }

    } catch {
      case _@e => e.printStackTrace()
    }

  }


  /* Bootstrap node type bundle in local osgi environment */
  def installNodeTyp(ct: DeployUnit): Boolean = {
    val url: List[String] = CommandHelper.buildAllQuery(ct)
    url.exists(u => installBundle(u))
  }

  def installBundle(url: String): Boolean = {
    try {
      bundle = EmbeddedOSGiEnv.getFwk.getBundleContext.installBundle(url)
      bundle.update

      bundle.start()
      true
    } catch {
      case e: BundleException if (e.getType == BundleException.DUPLICATE_BUNDLE_ERROR) => {
        bundle.update
        true
      }
      case _@e => {
        e.printStackTrace()
        false
      }
    }
  }


}