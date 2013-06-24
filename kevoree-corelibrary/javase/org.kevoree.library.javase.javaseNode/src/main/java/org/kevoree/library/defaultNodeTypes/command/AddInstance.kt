package org.kevoree.library.defaultNodeTypes.command

import org.kevoree.Instance
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.api.PrimitiveCommand
import org.kevoree.ContainerRoot
import org.kevoree.framework.kaspects.TypeDefinitionAspect
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
import org.kevoree.DeployUnit
import org.kevoree.log.Log
import org.kevoree.framework.AbstractNodeType

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

class AddInstance(val c: Instance, val nodeName: String, val modelservice: KevoreeModelHandlerService, val kscript: KevScriptEngineFactory, val bs: org.kevoree.api.Bootstraper, val nt : AbstractNodeType): PrimitiveCommand, Runnable {

    private val typeDefinitionAspect = TypeDefinitionAspect()
    var deployUnit : DeployUnit? = null
    var nodeTypeName : String? = null
    var tg : ThreadGroup? = null

    override fun execute(): Boolean {
        val model = c.getTypeDefinition()!!.eContainer() as ContainerRoot
        val node = model.findNodesByID(nodeName)
        deployUnit = typeDefinitionAspect.foundRelevantDeployUnit(c.getTypeDefinition()!!, node!!)!!
        val nodeType = node!!.getTypeDefinition()
        nodeTypeName = typeDefinitionAspect.foundRelevantHostNodeType(nodeType as NodeType, c.getTypeDefinition()!!)!!.getName()
        var subThread : Thread? = null
        try {
            tg = ThreadGroup("kev/"+c.path()!!)
            subThread = Thread(tg,this)
            subThread!!.start()
            subThread!!.join()
            KevoreeDeployManager.putRef(c.javaClass.getName()+"_tg", c.getName(), tg!!)
            return true
        } catch(e: Throwable) {
            if(subThread != null){
                try {
                    subThread!!.stop() //kill sub thread
                } catch(t : Throwable){
                    //ignore killing thread
                }
            }
            val message = "Could not start the instance " + c.getName() + ":" + c.getTypeDefinition()!!.getName() + "\n"/*+ " maybe because one of its dependencies is missing.\n"
        message += "Please check that all dependencies of your components are marked with a 'bundle' type (or 'kjar' type) in the pom of the component/channel's project.\n"*/
            Log.error(message, e)
            return false
        }
    }

    override fun undo() {
        RemoveInstance(c, nodeName, modelservice, kscript, bs,nt).execute()
    }

    public override fun run() {
        val beanClazz = bs.getKevoreeClassLoaderHandler().getKevoreeClassLoader(deployUnit)!!.loadClass(c.getTypeDefinition()!!.getBean())
        val newBeanInstance = beanClazz!!.newInstance()
        var newBeanKInstanceWrapper :KInstance? = null
        if(c is ComponentInstance){
            newBeanKInstanceWrapper = KevoreeComponent(newBeanInstance as AbstractComponentType,nodeName,c.getName(),modelservice,bs,kscript,nt.getDataSpaceService())
            (newBeanKInstanceWrapper as KevoreeComponent).initPorts(nodeTypeName!!,c,tg!!)
        }
        if(c is Group){
            newBeanKInstanceWrapper = KevoreeGroup(newBeanInstance as AbstractGroupType,nodeName,c.getName(),modelservice,bs,kscript,nt.getDataSpaceService())
        }
        if(c is Channel){
            newBeanKInstanceWrapper = ChannelTypeFragmentThread(newBeanInstance as AbstractChannelFragment,nodeName,c.getName(),modelservice,bs,kscript,nt.getDataSpaceService(),tg!!)
            (newBeanKInstanceWrapper as ChannelTypeFragmentThread).initChannel()
        }

        KevoreeDeployManager.putRef(c.javaClass.getName(), c.getName(), newBeanInstance!!)
        KevoreeDeployManager.putRef(c.javaClass.getName()+"_wrapper", c.getName(), newBeanKInstanceWrapper!!)
    }



}