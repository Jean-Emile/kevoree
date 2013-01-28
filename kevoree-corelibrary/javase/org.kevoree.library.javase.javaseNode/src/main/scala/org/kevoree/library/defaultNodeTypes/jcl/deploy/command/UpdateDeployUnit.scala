package org.kevoree.library.defaultNodeTypes.jcl.deploy.command

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

import org.kevoree.DeployUnit
import org.slf4j.LoggerFactory
import org.kevoree.framework.{FileNIOHelper}
import java.io.{FileInputStream, File}
import java.util.Random
import org.kevoree.api.PrimitiveCommand
import org.kevoree.kcl.KevoreeJarClassLoader

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 26/01/12
 * Time: 16:35
 */

case class UpdateDeployUnit(du : DeployUnit,bs : org.kevoree.api.Bootstraper) extends EndAwareCommand {

  val logger = LoggerFactory.getLogger(this.getClass)
  var lastKCL : KevoreeJarClassLoader = null
  var random = new Random

  def undo() {
    if(lastKCL != null){
      bs.getKevoreeClassLoaderHandler.removeDeployUnitClassLoader(du)
      bs.getKevoreeClassLoaderHandler.attachKCL(du,lastKCL)
    }
  }

  def execute(): Boolean = {
    try {
      lastKCL = bs.getKevoreeClassLoaderHandler.getKevoreeClassLoader(du)
      bs.getKevoreeClassLoaderHandler.removeDeployUnitClassLoader(du)
      bs.getKevoreeClassLoaderHandler.installDeployUnit(du)
      true
    } catch {
      case _@ e =>logger.debug("error ",e);false
    }
  }

  def doEnd {
    lastKCL = null
  }
}