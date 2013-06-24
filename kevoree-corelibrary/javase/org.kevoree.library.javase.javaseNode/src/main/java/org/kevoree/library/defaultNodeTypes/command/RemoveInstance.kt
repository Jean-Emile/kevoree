package org.kevoree.library.defaultNodeTypes.command

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

import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager
import org.kevoree.*
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.api.service.core.script.KevScriptEngineFactory
import org.kevoree.api.PrimitiveCommand
import org.kevoree.framework.KevoreeGeneratorHelper
import org.kevoree.framework.kaspects.TypeDefinitionAspect
import org.kevoree.log.Log
import org.kevoree.framework.AbstractNodeType

class RemoveInstance(val c: Instance, val nodeName: String, val modelservice: KevoreeModelHandlerService, val kscript: KevScriptEngineFactory, val bs: org.kevoree.api.Bootstraper, val nt : AbstractNodeType): PrimitiveCommand {

    private val typeDefinitionAspect = TypeDefinitionAspect()

    override fun undo() {
        try {
            AddInstance(c, nodeName, modelservice, kscript, bs,nt).execute()
            UpdateDictionary(c, nodeName).execute()
        } catch(e: Exception) {
        }
    }

    override fun execute(): Boolean {
        Log.debug("CMD REMOVE INSTANCE EXECUTION  - " + c.getName() + " - type - " + c.getTypeDefinition()!!.getName())
        try {
            /*
            val instanceRef = KevoreeDeployManager.getRef(c.javaClass.getName(), c.getName())
            val model = c.getTypeDefinition()!!.eContainer() as ContainerRoot
            val node = model.findNodesByID(nodeName)
            val deployUnit = typeDefinitionAspect.foundRelevantDeployUnit(c.getTypeDefinition(), node!!)
            val nodeType = node!!.getTypeDefinition()
            val nodeTypeName = typeDefinitionAspect.foundRelevantHostNodeType(nodeType as NodeType, c.getTypeDefinition())!!.getName()
            val activatorPackage = KevoreeGeneratorHelper().getTypeDefinitionGeneratedPackage(c.getTypeDefinition(), nodeTypeName)
            val factoryName = activatorPackage + "." + c.getTypeDefinition()!!.getName() + "Factory"
            val clazz = bs.getKevoreeClassLoaderHandler().getKevoreeClassLoader(deployUnit)!!.loadClass(factoryName)
            val clazzInstance = clazz!!.newInstance()
            val kevoreeFactory = clazzInstance as KevoreeInstanceFactory
            val activator = kevoreeFactory.remove(c.getName())
            if(activator != null){
                activator!!.stop()
            }  else
            {
                logger.error(" TypeCache "+c.getName()+"does not exist.")
                return false
            } */

            KevoreeDeployManager.clearRef(c.javaClass.getName()+"_tg", c.getName())
            KevoreeDeployManager.clearRef(c.javaClass.getName()+"_wrapper", c.getName())
            KevoreeDeployManager.clearRef(c.javaClass.getName(), c.getName())
            return true
        } catch(e: Exception){
            Log.error("RemoveInstance "+c.getName() + " - type - " + c.getTypeDefinition()!!.getName()+" ",e)
            return false
        }
    }

}
