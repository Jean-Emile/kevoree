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
package org.kevoree.merger.tests

import org.junit.{Test, Before}
import org.kevoree.merger.KevoreeMergerComponent
import org.kevoree.api.service.core.merger.MergerService
import scala.collection.JavaConversions._

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 29/11/12
 * Time: 07:29
 */
class OppositeMergeTest extends MergerTestSuiteHelper {

  var component: MergerService = null

  @Before def initialize() {
    component = new KevoreeMergerComponent
  }


  @Test def verifySimpleMerge1() {
    val mergedModel = component.merge(emptyModel, model("kloud/k1.kev"))
    mergedModel testSave ("kloud","k1m.kev")
  }

  @Test def verifySimpleMerge2() {
    val mergedModel = component.merge(model("kloud/m1.kev"), model("kloud/m1.kev"))
    mergedModel testSave ("kloud","m1res.kev")
  }

  @Test def verifySimpleMerge3() {

    val mbase = model("kloud/m2.kev")
    mbase testSave ("kloud","m2res.kev")

    mbase.getNodes.foreach{ n =>
      if(n.getName == "editor_node"){
         assert(n.getHosts.size == 1,"Duplucate detected in editor_node children")
      }
    }

    val mergedModel = component.merge(model("kloud/m2.kev"), model("kloud/m2.kev"))
    mergedModel.getNodes.foreach{ n =>
      if(n.getName == "editor_node"){
        assert(n.getHosts.size == 1,"Duplucate detected in editor_node children")
      }
    }
    mergedModel testSave ("kloud","m2res.kev")
  }

  @Test def verifySimpleMerge4() {
    val mergedModel = component.merge(emptyModel, model("kloud/m2.kev"))

   // println(mergedModel.findNodesByID("editor_node").getHostsForJ)
   // println(mergedModel.findNodesByID("node0").getName)

    mergedModel.getNodes.foreach {
      n =>
        if (n.getName == "editor_node") {
          println(n.getHosts.size)
          assert(n.getHosts.size == 1, "Duplucate detected in editor_node children")
        }
    }
    mergedModel testSave("kloud", "m22res.kev")
  }

}
