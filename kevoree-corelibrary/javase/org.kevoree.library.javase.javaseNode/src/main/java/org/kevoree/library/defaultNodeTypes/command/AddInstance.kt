package org.kevoree.library.defaultNodeTypes.command

import org.kevoree.Instance
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.api.PrimitiveCommand
import org.slf4j.LoggerFactory
import org.kevoree.ContainerRoot
import org.kevoree.framework.aspects.TypeDefinitionAspect
import org.kevoree.NodeType
import org.kevoree.framework.KevoreeGeneratorHelper
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager
import org.kevoree.framework.AbstractChannelFragment
import org.kevoree.framework.AbstractComponentType
import org.kevoree.framework.AbstractGroupType
import org.kevoree.ComponentInstance
import org.kevoree.framework.KInstance
import org.kevoree.framework.KevoreeComponent
import org.kevoree.Group
import org.kevoree.framework.KevoreeGroup
import org.kevoree.Channel
import org.kevoree.framework.ChannelTypeFragmentThread

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


/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 26/01/12
 * Time: 17:53
 */

class AddInstance(val c: Instance, val nodeName: String, val modelservice: KevoreeModelHandlerService, val kscript: KevScriptEngineFactory, val bs: org.kevoree.api.Bootstraper): PrimitiveCommand {

    override fun execute(): Boolean {
        val model = c.getTypeDefinition()!!.eContainer() as ContainerRoot
        val node = model.findNodesByID(nodeName)
        val deployUnit = org.kevoree.framework.aspects.TypeDefinitionAspect(c.getTypeDefinition()).foundRelevantDeployUnit(node)
        val nodeType = node!!.getTypeDefinition()
        val nodeTypeName = org.kevoree.framework.aspects.TypeDefinitionAspect(c.getTypeDefinition()).foundRelevantHostNodeType(nodeType as NodeType, c.getTypeDefinition())!!.get()!!.getName()
        try {

            val beanClazz = bs.getKevoreeClassLoaderHandler().getKevoreeClassLoader(deployUnit)!!.loadClass(c.getTypeDefinition()!!.getBean())
            val newBeanInstance = beanClazz!!.newInstance()
            var newBeanKInstanceWrapper :KInstance? = null
            if(c is ComponentInstance){
                newBeanKInstanceWrapper = KevoreeComponent(newBeanInstance as AbstractComponentType,nodeName,c.getName(),modelservice)
                (newBeanKInstanceWrapper as KevoreeComponent).initPorts(nodeTypeName,c)
            }
            if(c is Group){
                newBeanKInstanceWrapper = KevoreeGroup(newBeanInstance as AbstractGroupType,nodeName,c.getName(),modelservice)
            }
            if(c is Channel){
                newBeanKInstanceWrapper = ChannelTypeFragmentThread(newBeanInstance as AbstractChannelFragment,nodeName,c.getName(),modelservice)
                (newBeanKInstanceWrapper as ChannelTypeFragmentThread).initChannel()
            }


            println("AddRef=>"+c.javaClass.getName()+"/"+c.getName())
            println("AddRef=>"+c.javaClass.getName()+"_wrapper"+"/"+c.getName())


            KevoreeDeployManager.putRef(c.javaClass.getName(), c.getName(), newBeanInstance!!)
            KevoreeDeployManager.putRef(c.javaClass.getName()+"_wrapper", c.getName(), newBeanKInstanceWrapper!!)

//            newInstance.setKevScriptEngineFactory(kscript)

            /*
            if(newInstance is KevoreeGroupActivator){
                ((newInstance as KevoreeGroupActivator).groupActor() as AbstractGroupType).setBootStrapperService(bs)
            }
            if(newInstance is KevoreeChannelFragmentActivator){
                ((newInstance as KevoreeChannelFragmentActivator).channelActor() as AbstractChannelFragment).setBootStrapperService(bs)
            }
            if(newInstance is KevoreeComponentActivator){
                ((newInstance as KevoreeComponentActivator).componentActor()!!.getKevoreeComponentType() as AbstractComponentType).setBootStrapperService(bs)
            } */
            return true
        } catch(e: Exception) {
            val message = "Could not start the instance " + c.getName() + ":" + c.getTypeDefinition()!!.getName() + "\n"/*+ " maybe because one of its dependencies is missing.\n"
        message += "Please check that all dependencies of your components are marked with a 'bundle' type (or 'kjar' type) in the pom of the component/channel's project.\n"*/
            logger.error(message, e)
            return false
        }
    }

    var logger = LoggerFactory.getLogger(this.javaClass)!!

    override fun undo() {
        RemoveInstance(c, nodeName, modelservice, kscript, bs).execute()
    }

}