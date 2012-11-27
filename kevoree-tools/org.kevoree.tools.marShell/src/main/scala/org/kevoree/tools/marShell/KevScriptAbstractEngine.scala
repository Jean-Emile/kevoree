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
package org.kevoree.tools.marShell

import org.kevoree.api.service.core.script.KevScriptEngine
import java.util.HashMap
import org.kevoree.cloner.ModelCloner
import parser.KevsParser
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._


/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 05/02/12
 * Time: 16:15
 */

trait KevScriptAbstractEngine extends KevScriptEngine {

  protected var scriptBuilder = new StringBuilder
  protected var varMap = new HashMap[String, String]()
  protected val modelCloner = new ModelCloner
  protected val parser = new KevsParser
  protected val logger = LoggerFactory.getLogger(this.getClass)

  def addVariable(name: String, value: String): KevScriptEngine = {
    /*if(varMap.containsKey(name)){
      replaceVariable(name,varMap.get(name))
    }*/
    varMap.put(name, value)
    this
  }

  def append(scriptStatement: String): KevScriptEngine = {
    scriptBuilder.append(resolveVariables(scriptStatement))
    scriptBuilder.append("\n")
    this
  }

  def clearScript() {
    scriptBuilder.clear()
  }
  /*
  protected def replaceVariable(name: String, value: String) = {
    val unresolveScript = scriptBuilder.toString().replace("{" + name + "}", value)
    scriptBuilder.clear()
    scriptBuilder.append(unresolveScript)
  } */

  protected def resolveVariables(stm:String): String = {
    var unresolveScript = stm
    varMap.foreach {
      varR =>
        unresolveScript = unresolveScript.replace("{" + varR._1 + "}", varR._2)
    }
    unresolveScript = unresolveScript.replace("'", "\"")
    unresolveScript
  }

  protected def getScript : String = {
    "{"+scriptBuilder.toString()+"}"
  }

}
