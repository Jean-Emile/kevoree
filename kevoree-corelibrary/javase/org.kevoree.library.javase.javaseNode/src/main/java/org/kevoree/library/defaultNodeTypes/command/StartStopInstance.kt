package org.kevoree.library.defaultNodeTypes.command

import org.kevoree.ContainerRoot
import org.kevoree.Instance
import org.kevoree.framework.KInstance
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager

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

class StartStopInstance(c: Instance, nodeName: String, val start: Boolean): LifeCycleCommand(c, nodeName) {

    override fun undo() {
        StartStopInstance(c, nodeName, !start).execute()
    }

    override fun execute(): Boolean {

        val root = c.getTypeDefinition()!!.eContainer() as ContainerRoot
        val ref = KevoreeDeployManager.getRef(c.javaClass.getName()+"_wrapper", c.getName())
        if(ref != null && ref is KInstance){
            val iact = ref as KInstance
            Thread.currentThread().setContextClassLoader(iact.javaClass.getClassLoader())
            if(start){
                Thread.currentThread().setName("KevoreeStartInstance" + c.getName())
                return iact.kInstanceStart(root)
            } else {
                Thread.currentThread().setName("KevoreeStopInstance" + c.getName())
                val res = iact.kInstanceStop(root)
                Thread.currentThread().setContextClassLoader(null)
                return res
            }
        } else {
            return false
        }
    }

}
