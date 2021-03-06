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

package org.kevoree.merger.sub

import org.kevoree.framework.kaspects.DeployUnitAspect
import org.kevoree.KevoreeFactory
import org.kevoree.ContainerRoot
import org.kevoree.DeployUnit
import org.kevoree.log.Log
import org.kevoree.Repository
import org.kevoree.merger.aspects.KevoreeAspects._
import scala.collection.JavaConversions._


trait RepositoryMerger {
  private val deployUnitAspect = new DeployUnitAspect()

  //EXPECT TYPE DEFINITION TO BE MERGE BEFORE THIS STEP
  def mergeRepositories(actualModel: ContainerRoot, modelToMerge: ContainerRoot) {
    val kevoreeFactory = new org.kevoree.impl.DefaultKevoreeFactory
    val ctRepo: List[Repository] = modelToMerge.getRepositories.toList
    ctRepo.foreach {
      toMergeRepo =>
        actualModel.getRepositories.find(lr => lr.getUrl == toMergeRepo.getUrl) match {
          case Some(found_repo) => mergeRepository(actualModel, found_repo, toMergeRepo)
          case None => {
            val newrepo = kevoreeFactory.createRepository
            newrepo.setUrl(toMergeRepo.getUrl)
            actualModel.addRepositories(newrepo)
            mergeRepository(actualModel, newrepo, toMergeRepo)
          }
        }
    }
  }

  def mergeRepository(actualRoot: ContainerRoot, actualRepository: Repository, toMergeRepository: Repository): Unit = {
    val toMergeUnits: List[DeployUnit] = toMergeRepository.getUnits.toList
    toMergeUnits.foreach {
      unit =>
      //ACTUAL UNIT
        val found_unit = actualRoot.getDeployUnits.find(du => deployUnitAspect.isModelEquals(du, unit) /*&& du.getHashcode == unit.getHashcode*/)
        found_unit match {
          case None => {
            Log.debug("Merger Error !!!!! Repository Incomplete")
          }
          case Some(funit) => {
            //CLEAN REPO FROM OLD DEPLOY UNIT
            actualRepository.getUnits.filter(u => deployUnitAspect.isModelEquals(u, funit) /*&& u.getHashcode != funit.getHashcode*/).foreach {
              oldunit =>
                actualRepository.removeUnits(oldunit)
            }
            //ADD NEW UNIT TO REPO
            actualRepository.getUnits.find(u => u == funit) match {
              case None => actualRepository.addUnits(funit)
              case Some(u) => //NOTHING TO DO
            }
          }
        }
    }
  }

}
