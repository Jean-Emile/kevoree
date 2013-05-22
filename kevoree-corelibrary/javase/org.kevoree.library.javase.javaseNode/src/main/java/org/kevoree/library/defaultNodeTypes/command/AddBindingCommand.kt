package org.kevoree.library.defaultNodeTypes.command

import org.kevoree.MBinding
import org.kevoree.api.PrimitiveCommand
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager
import org.kevoree.ComponentInstance
import org.kevoree.framework.AbstractComponentType
import org.kevoree.framework.AbstractChannelFragment
import org.kevoree.framework.message.FragmentBindMessage
import org.kevoree.framework.KevoreePort
import org.kevoree.framework.KevoreeChannelFragment
import org.kevoree.framework.message.PortBindMessage
import org.kevoree.framework.KevoreeComponent
import org.kevoree.log.Log

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



class AddBindingCommand(val c: MBinding, val nodeName: String): PrimitiveCommand {

    override fun undo() {
        RemoveBindingCommand(c, nodeName).execute()
    }
    override fun execute(): Boolean {
        if(c == null){
            return false
        }else{
            val kevoreeChannelFound = KevoreeDeployManager.getRef(c.getHub().javaClass.getName()+"_wrapper", c.getHub()!!.getName())
            val kevoreeComponentFound = KevoreeDeployManager.getRef((c.getPort()!!.eContainer() as ComponentInstance).javaClass.getName(), (c.getPort()!!.eContainer()as ComponentInstance).getName())
            if(kevoreeChannelFound != null && kevoreeComponentFound != null && kevoreeComponentFound is AbstractComponentType){
                val casted = kevoreeComponentFound as AbstractComponentType
                val channelCasted = kevoreeChannelFound as KevoreeChannelFragment
                val portName = c.getPort()!!.getPortTypeRef()!!.getName()
                val foundNeedPort = casted.getNeededPorts()!!.get(portName)
                val foundHostedPort = casted.getHostedPorts()!!.get(portName)
                if(foundNeedPort == null && foundHostedPort == null){
                    Log.info("Port instance not found in component")
                    Log.info("Look for " + portName);
                    Log.info("" + casted.getNeededPorts()!!.containsKey(portName));
                    Log.info("" + casted.getHostedPorts()!!.containsKey(portName));
                    return false
                }

                if (foundNeedPort != null) {
                    /* Bind port to Channel */
                    val newbindmsg = FragmentBindMessage(channelCasted,c.getHub()!!.getName(),nodeName)
                    return (foundNeedPort as KevoreePort).processAdminMsg(newbindmsg)
                }
                if(foundHostedPort != null){
                    val compoName = (c.getPort()!!.eContainer() as ComponentInstance).getName()
                    val bindmsg = PortBindMessage(foundHostedPort as KevoreePort,nodeName,compoName,(foundHostedPort as KevoreePort).getName()!!)
                    return channelCasted.processAdminMsg(bindmsg)
                }
                return false
            } else {
                Log.error("Error while apply binding , channelFound="+kevoreeChannelFound+",componentFound="+kevoreeComponentFound)
                return false
            }
        }
    }

    fun toString(): String {
        return "AddBindingCommand / "+c.getHub()!!.getName();
    }
}
