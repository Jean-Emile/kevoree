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

import org.slf4j.LoggerFactory
import org.kevoree.{ContainerNode, ContainerRoot}
import scala.collection.JavaConversions._


/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 12/12/11
 * Time: 14:38
 * To change this template use File | Settings | File Templates.
 */

trait TopologyResolver {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def resolveTopologyNodes(model: ContainerRoot) {
    model.getNodeNetworks.foreach {
      nn =>
        val iBy = nn.getInitBy()
        if (iBy != null){
          nn.setInitBy(resolveNodeInstance(model, iBy))
        }
        val rTNode = resolveNodeInstance(model, nn.getTarget)
        if (rTNode != null){
          nn.setTarget(rTNode)
        }
    }
  }

  private def resolveNodeInstance(model: ContainerRoot, node: ContainerNode): ContainerNode = {
    node match {
      case targetNodeName : UnresolvedNode => {
        model.findByQuery(targetNodeName.getQuery(),classOf[ContainerNode]) match {
          case foundNode : ContainerNode => foundNode
          case null => logger.error("Unconsitent model , node not found for name " + targetNodeName.getName()); null
        }
      }
      case _ => logger.error("Already Dictionary Value targetNodeName for value " + node); node
    }
  }


}