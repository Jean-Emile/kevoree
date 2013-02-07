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
package org.kevoree.merger.resolver

import org.kevoree.PortType
import org.kevoree.DeployUnit
import java.util.ArrayList
import org.kevoree.DictionaryType
import org.kevoree.TypeDefinition
import java.util.HashMap
import org.kevoree.KevoreeContainer

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 06/12/12
 * Time: 23:38
 */
class UnresolvedPortType(val unresolvedPortName: String): PortType {

    override var _factoryBean: String = ""
    override var _bean: String = ""
    override var _nature: String = ""
    override var _deployUnits_java_cache: List<DeployUnit>? = null
    override val _deployUnits: MutableList<DeployUnit> = ArrayList<DeployUnit>()
    override var _dictionaryType: DictionaryType? = null
    override var _superTypes_java_cache: List<TypeDefinition>? = null
    override val _superTypes: HashMap<Any, TypeDefinition> = HashMap<Any, TypeDefinition>()
    override var _name: String = ""
    override var internal_eContainer: KevoreeContainer? = null
    override var internal_unsetCmd: (() -> Unit)? = null
    override var internal_readOnlyElem: Boolean = false
    override var _synchrone: Boolean = false
    override fun getName(): String {
        return unresolvedPortName
    }

}