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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kevoree.kompare.tests

import org.kevoree.framework.KevoreeXmiHelper
import org.kevoreeadaptation.AdaptationModel
import org.kevoreeadaptation._
import org.scalatest.junit.JUnitSuite
 import org.kevoree._
import scala.collection.JavaConversions._

trait KompareSuite extends JUnitSuite {

  /* UTILITY METHOD */
  def model(url: String): ContainerRoot = {
    if (this.getClass.getClassLoader.getResource(url) == null) {
      println("Warning File not found for test !!!")
    }
    KevoreeXmiHelper.instance$.load(this.getClass.getClassLoader.getResource(url).getPath)
  }

  implicit def utilityKompareModel(self: AdaptationModel) = RichAdaptationModel(self)

  implicit def richRoot(self: ContainerRoot) = RichContainerRoot(self)

  val kevoreeFactory = new org.kevoree.impl.DefaultKevoreeFactory

  def emptyModel = kevoreeFactory.createContainerRoot

}

case class RichAdaptationModel(self: AdaptationModel) {

  def verifySize(size: Int) = {
    assert(self.getAdaptations.size == size,"Size not equals found="+self.getAdaptations.size+", must be ="+size)
  }

  def shouldContain[A](c: String, refName: String) = {
    assert(
      self.getAdaptations.exists(adaptation => {
        adaptation.getRef match {
          case e: Instance if (adaptation.getPrimitiveType.getName.contains(c)) => e.getName == refName
          case e: TypeDefinition if (adaptation.getPrimitiveType.getName.contains(c)) => e.getName == refName
          case _ => false
        }
      })
    )
  }

  def shouldContainSize[A](c: String, nb: Int) = {
    assert(
      self.getAdaptations.filter(adaptation => adaptation.getPrimitiveType.getName.contains(c)).size == nb
    )
  }


  def shouldNotContain(c: String) = {
    assert(
      self.getAdaptations.forall(adaptation => !adaptation.getPrimitiveType.getName.contains(c))
    )
  }

  def print = {

    println("Adaptations "+self.getAdaptations.size())
    self.getAdaptations.toList.foreach {
      adapt =>
        println(adapt.getPrimitiveType.getName)
        adapt.getRef match {
          case i: DeployUnit => println("=>" + i.getUnitName)
          case i: TypeDefinition => println("=>" + i.getName)
          case i: Instance => println("=>" + i.getName)
          case i: MBinding => {
            println("=>" + i.getHub.getName + "->" + i.getPort.getPortTypeRef.getName + "-" + i.getPort.eContainer.asInstanceOf[NamedElement].getName)
          }
          case _ => {
            println(">"+adapt.getRef.getClass.getName)
          }

        }

    }
  }
}


case class RichContainerRoot(self: ContainerRoot) {

  def setLowerHashCode: ContainerRoot = {
    self.getDeployUnits.foreach(du => du.setHashcode(0 + ""))
    self
  }


}