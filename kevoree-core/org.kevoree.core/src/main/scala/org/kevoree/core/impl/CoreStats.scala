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
package org.kevoree.core.impl

/**
 * Created by IntelliJ IDEA.
 * User: gnain
 * Date: 07/12/11
 * Time: 09:03
 * To change this template use File | Settings | File Templates.
 */

object CoreStats {

  var updateIndex = -1

  def getExecutionIndex: Int = {
    updateIndex += 1;
    updateIndex
  }

  def getLastExecutionIndex: Int = updateIndex

  var update: Map[Int, Long] = Map[Int, Long]()
  var kompare: Map[Int, Long] = Map[Int, Long]()
  var deploy: Map[Int, Long] = Map[Int, Long]()
  var checking: Map[Int, Long] = Map[Int, Long]()
  var cloneMap: Map[Int, Long] = Map[Int, Long]()

  def printStats: String = {
    var res = "\nIndex\tCheck\tClone\tKompare\tDeploy\n"

    for (i <- 0 to updateIndex) {
      res += i + "\t"
      res += checking.getOrElse(i,"-") + "\t"
      res += cloneMap.getOrElse(i,"-") + "\t"
      res += kompare.getOrElse(i,"-") + "\t"
      res += deploy.getOrElse(i,"-") + "\t"
      //res += update.getOrElse(i,"-") + "\t"
      res += "\n"
    }
    res
  }

  def printLastStats: String = {
    var res = "\nIndex\tCheck\tClone\tKompare\tDeploy\n"
      res += updateIndex + "\t"
      res += checking.getOrElse(updateIndex,"-") + "\t"
      res += cloneMap.getOrElse(updateIndex,"-") + "\t"
      res += kompare.getOrElse(updateIndex,"-") + "\t"
      res += deploy.getOrElse(updateIndex,"-") + "\t"
      //res += update.getOrElse(updateIndex,"-") + "\t"
      res += "\n"
    res
  }
}