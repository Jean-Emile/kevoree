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
package org.kevoree.merger.resolver

import org.kevoree.ContainerNode
import java.util.HashMap
import org.kevoree.KevoreeContainer
import org.kevoree.ComponentInstance
import java.util.ArrayList
import org.kevoree.TypeDefinition
import org.kevoree.Dictionary

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 26/02/12
 * Time: 12:42
 *
 * @author Erwan Daubert
 * @version 1.0
 */

class UnresolvedChildNode(val childName: String): ContainerNode {
    override var _components_java_cache: List<ComponentInstance>? = null
    override val _components: HashMap<Any, ComponentInstance> = HashMap<Any, ComponentInstance>()
    override var _hosts_java_cache: List<ContainerNode>? = ArrayList<ContainerNode>()
    override val _hosts: HashMap<Any, ContainerNode> = HashMap<Any, ContainerNode>()
    override var _host: ContainerNode? = null
    override var internal_eContainer: KevoreeContainer? = null
    override var internal_unsetCmd: (() -> Unit)? = null
    override var internal_readOnlyElem: Boolean = false
    override var _metaData: String = ""
    override var _typeDefinition: TypeDefinition? = null
    override var _dictionary: Dictionary? = null
    override var _name: String = ""

    override fun getName(): String {
        return childName
    }
}
