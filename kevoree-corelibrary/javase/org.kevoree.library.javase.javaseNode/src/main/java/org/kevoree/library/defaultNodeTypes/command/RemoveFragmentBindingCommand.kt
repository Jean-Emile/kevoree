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

import org.kevoree.framework.message.FragmentUnbindMessage
import org.kevoree.Channel
import org.kevoree.api.PrimitiveCommand
import org.kevoree.library.defaultNodeTypes.context.KevoreeDeployManager
import org.kevoree.framework.message.FragmentBindMessage
import org.kevoree.framework.KevoreeChannelFragment

class RemoveFragmentBindingCommand(val c: Channel, val remoteNodeName: String, val nodeName: String): PrimitiveCommand {

    override fun execute(): Boolean {

        val kevoreeChannelFound = KevoreeDeployManager.getRef(c.javaClass.getName()+"_wrapper", c.getName()) as KevoreeChannelFragment
        if(kevoreeChannelFound != null){
            val bindmsg = FragmentUnbindMessage(c.getName(),remoteNodeName)
            return (kevoreeChannelFound.processAdminMsg(bindmsg))
        } else {
            return false
        }
    }

    override fun undo() {
        AddFragmentBindingCommand(c, remoteNodeName, nodeName).execute()
    }

}
