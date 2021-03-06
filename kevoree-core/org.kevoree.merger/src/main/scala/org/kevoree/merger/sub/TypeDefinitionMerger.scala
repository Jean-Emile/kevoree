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

import org.kevoree.framework.kaspects.ContainerRootAspect
import org.kevoree.framework.kaspects.TypeDefinitionAspect
import org.kevoree.merger.Merger
import org.kevoree.merger.aspects.KevoreeAspects._
import org.kevoree._
import merger.resolver.{UnresolvedTypeDefinition}
import scala.collection.JavaConversions._

trait TypeDefinitionMerger extends Merger with DictionaryMerger with PortTypeMerger with DeployUnitMerger {

  private val kevoreeFactory = new org.kevoree.impl.DefaultKevoreeFactory
  private val containerRootAspect = new ContainerRootAspect()
  private val typeDefinitionAspect = new TypeDefinitionAspect()

  //TYPE DEFINITION MERGER ENTRYPOINT
  def mergeTypeDefinition(actualModel: ContainerRoot, modelToMerge: ContainerRoot): Unit = {

    modelToMerge.getTypeDefinitions.foreach {
      toMergeTypeDef =>
        actualModel.getTypeDefinitions.find(actualTypeDef => actualTypeDef.isModelEquals(toMergeTypeDef)) match {
          case Some(found_type_definition) => {
            val root = found_type_definition.eContainer.asInstanceOf[ContainerRoot]
            if (typeDefinitionAspect.isUpdated(found_type_definition, toMergeTypeDef)) {
              if (found_type_definition.contractChanged(toMergeTypeDef)) {
                consistencyImpacted(root, found_type_definition, toMergeTypeDef)
              } else {
                mergeConsistency(root, found_type_definition, toMergeTypeDef)
              }
            } else {
              //cleanCrossReference(found_type_definition, toMergeTypeDef)
              org.kevoree.log.Log.debug("No update found for type {}",toMergeTypeDef.getName)
            }
          }
          //SIMPLE CASE ? JUST MERGE THE NEW TYPE DEFINITION
          case None => mergeNewTypeDefinition(actualModel, toMergeTypeDef)
        }
    }
  }

  /*
  def cleanCrossReference(actuelTypeDefinition: TypeDefinition, toMergeTypeDef: TypeDefinition) {
     (toMergeTypeDef.getDeployUnits.toList ++ actuelTypeDefinition.getDeployUnits).foreach{ dp =>
       dp.getTargetNodeType.map { targetNodeType =>
         dp.setTargetNodeType(Some(UnresolvedNodeType(targetNodeType.getName)))
       }
     }
  } */


  private def mergeConsistency(root: ContainerRoot, actuelTypeDefinition: TypeDefinition,
                               newTypeDefinition: TypeDefinition) = {

    //UPDATE & MERGE DEPLOYS UNIT
    //CONCAT & MERGE BOTH TYPE DEF DEPLOY UNIT
    val allDeployUnits = newTypeDefinition.getDeployUnits.toList ++ actuelTypeDefinition.getDeployUnits
    actuelTypeDefinition.removeAllDeployUnits()
    allDeployUnits.foreach {
      ldu =>
        val merged = mergeDeployUnit(root, ldu /*, newTypeDefinition.getDeployUnits.contains(ldu) */)
        if (!actuelTypeDefinition.getDeployUnits.contains(merged)) {
          actuelTypeDefinition.addDeployUnits(merged)
        }
    }

  }

  private def consistencyImpacted(root: ContainerRoot, actuelTypeDefinition: TypeDefinition,
                                  newTypeDefinition: TypeDefinition) = {
    org.kevoree.log.Log.debug("consistency Impacted= {}", actuelTypeDefinition.getName)
    val kevoreeFactory = new org.kevoree.impl.DefaultKevoreeFactory
    //REMOVE OLD AND ADD NEW TYPE

    root.removeTypeDefinitions(actuelTypeDefinition)
    mergeNewTypeDefinition(root, newTypeDefinition, true)

    //PARTICULAR CASE - CHECK
    if (newTypeDefinition.isInstanceOf[NodeType]) {
      val nodeType = newTypeDefinition.asInstanceOf[NodeType]
      val pl = nodeType.getManagedPrimitiveTypes
      nodeType.removeAllManagedPrimitiveTypes()
      pl.foreach {
        pll =>
          if (!nodeType.getManagedPrimitiveTypes.exists(ap => ap.getName == pll.getName)) {
            nodeType.addManagedPrimitiveTypes(mergeAdaptationPrimitive(root, pll))
          }
      }
    }
    //UPDATE DEPLOYS UNIT
    val allDeployUnits = newTypeDefinition.getDeployUnits
    newTypeDefinition.removeAllDeployUnits()
    allDeployUnits.foreach {
      ndu =>
        val merged = mergeDeployUnit(root, ndu.asInstanceOf[DeployUnit], true)
        if (!newTypeDefinition.getDeployUnits.contains(merged)) {
          newTypeDefinition.addDeployUnits(merged)
        }
    }

    //PROCESS INSTANCE
    val listInstance = containerRootAspect.getAllInstances(root).filter(instance => instance.getTypeDefinition.isInstanceOf[UnresolvedTypeDefinition] && instance.getTypeDefinition.asInstanceOf[UnresolvedTypeDefinition].getName == newTypeDefinition.getName)
    listInstance.foreach {
      instance =>
        val kevoreeInstance = instance.asInstanceOf[Instance]

        //MERGE DICTIONARY
        if (kevoreeInstance.getDictionary()!=null) {
          if (newTypeDefinition.getDictionaryType()!=null) {
            mergeDictionary(kevoreeInstance.getDictionary(), newTypeDefinition.getDictionaryType())
          } else {
            // set to None the dictionary of the art2instance
            kevoreeInstance.setDictionary(null)
            org.kevoree.log.Log.debug("There is no dictionary type on the new type definition {}",newTypeDefinition.getName)
          }
        } else {
          org.kevoree.log.Log.debug("There is no dictionary type on the current type definition {}" ,kevoreeInstance.getName)
        }

        //SPECIFIC PROCESS
        kevoreeInstance match {
          case c: ComponentInstance => {
            val ct = newTypeDefinition.asInstanceOf[ComponentType]

            //MERGE PORT
            val providedPort = c.getProvided.toList.toList ++ List()
            providedPort.foreach {
              pport =>
                ct.getProvided.find(p => p.getName == pport.getPortTypeRef.getName) match {
                  case None => pport.removeAndUnbind()
                  case Some(ptref) => pport.setPortTypeRef(ptref)
                }
            }
            val requiredPort = c.getRequired.toList.toList ++ List()
            requiredPort.foreach {
              rport =>
                ct.getRequired.find(p => p.getName == rport.getPortTypeRef.getName) match {
                  case None => rport.removeAndUnbind()
                  case Some(ptref) => rport.setPortTypeRef(ptref)
                }
            }

            //CREATE NEW PORT
            ct.getProvided.foreach {
              newpt =>
                c.getProvided.find(p => p.getPortTypeRef == newpt) match {
                  case None => {
                    val newport = kevoreeFactory.createPort
                    newport.setPortTypeRef(newpt)
                    c.addProvided(newport)
                  }
                  case Some(p) => //OK PORT ALREADY EXIST
                }
            }
            ct.getRequired.foreach {
              newpt =>
                c.getRequired.find(p => {
                  p.getPortTypeRef == newpt
                }) match {
                  case None => {
                    val newport = kevoreeFactory.createPort
                    newport.setPortTypeRef(newpt)
                    c.addRequired(newport)
                  }
                  case Some(p) => //OK PORT ALREADY EXIST
                }
            }

          }
          case _ => //NO SPECIFIC PROCESS FOR OTHER INSTANCE
        }

    }
  }

  /* MERGE A SIMPLE NEW TYPE DEFINITION */
  private def mergeNewTypeDefinition(actualModel: ContainerRoot, newTypeDefinition: TypeDefinition, force: Boolean = false) = {
    org.kevoree.log.Log.debug("addNewTypeDef {}", newTypeDefinition.getName)
    //MERGE TYPE DEPLOY UNITS
    val newTypeDefinitionDeployUnits = newTypeDefinition.getDeployUnits
    newTypeDefinition.removeAllDeployUnits()
    newTypeDefinitionDeployUnits.foreach {
      ndu =>
        newTypeDefinition.addDeployUnits(mergeDeployUnit(actualModel, ndu.asInstanceOf[DeployUnit], true))
    }
    //ADD RECUSIVE DEFINITON TO ROOT
    newTypeDefinition match {
      case ct: ChannelType => {
        actualModel.addTypeDefinitions(ct)
      }
      case ct: ComponentType => {
        actualModel.addTypeDefinitions(ct)
        ct.getProvided.foreach {
          ptref => ptref.setRef(mergePortType(actualModel, ptref.getRef))
        }
        ct.getRequired.foreach {
          ptref => ptref.setRef(mergePortType(actualModel, ptref.getRef))
        }
      }
      case nt: NodeType => {
        actualModel.addTypeDefinitions(nt)
        val pl = (nt.getManagedPrimitiveTypes.toList.toList ++ List())
        nt.removeAllManagedPrimitiveTypes()
        pl.foreach {
          pll =>
            if (!nt.getManagedPrimitiveTypes.exists(ap => ap.getName == pll.getName)) {
              nt.addManagedPrimitiveTypes(mergeAdaptationPrimitive(actualModel, pll))
            }
        }
        nt.getManagedPrimitiveTypeRefs.foreach {
          ref =>
              ref.setRef(mergeAdaptationPrimitive(actualModel, ref.getRef))
        }
      }
      case gt: GroupType => {
        actualModel.addTypeDefinitions(gt)
      }
      case pt: PortType => {
        /*println("PORTTYPE M ?? "+pt.toString)*//* MERGE BY COMPONENT TYPE */
      }
      case _@msg => org.kevoree.log.Log.info("Error uncatch type") // NO RECURSIVE FOR OTHER TYPE
    }
  }

  def mergeAdaptationPrimitive(model: ContainerRoot, adaptation: AdaptationPrimitiveType): AdaptationPrimitiveType = {
    model.getAdaptationPrimitiveTypes.find(p => p.getName == adaptation.getName) match {
      case Some(p) => p
      case None => {
        val newT = kevoreeFactory.createAdaptationPrimitiveType
        newT.setName(adaptation.getName)
        model.addAdaptationPrimitiveTypes(newT)
        newT
      }
    }
  }

}
