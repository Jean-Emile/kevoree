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

package org.kevoree.tools.model2code

import japa.parser.ASTHelper
import japa.parser.ast.Comment
import japa.parser.ast.CompilationUnit
import japa.parser.ast.ImportDeclaration
import japa.parser.ast.PackageDeclaration
import japa.parser.ast.body.BodyDeclaration
import japa.parser.ast.body.ClassOrInterfaceDeclaration
import japa.parser.ast.body.MethodDeclaration
import japa.parser.ast.body.ModifierSet
import japa.parser.ast.body.Parameter
import japa.parser.ast.body.TypeDeclaration
import japa.parser.ast.body.VariableDeclaratorId
import japa.parser.ast.expr.AnnotationExpr
import japa.parser.ast.expr.ArrayInitializerExpr
import japa.parser.ast.expr.BooleanLiteralExpr
import japa.parser.ast.expr.Expression
import japa.parser.ast.expr.FieldAccessExpr
import japa.parser.ast.expr.MarkerAnnotationExpr
import japa.parser.ast.expr.MemberValuePair
import japa.parser.ast.expr.NameExpr
import japa.parser.ast.TypeParameter
import japa.parser.ast.`type`.Type
import japa.parser.ast.`type`.ClassOrInterfaceType
import japa.parser.ast.expr.NormalAnnotationExpr
import japa.parser.ast.expr.SingleMemberAnnotationExpr
import japa.parser.ast.expr.StringLiteralExpr
import japa.parser.ast.stmt.BlockStmt
import java.util.ArrayList
import java.util.Collections
import org.kevoree.ComponentType
import org.kevoree.ContainerRoot
import org.kevoree.MessagePortType
import org.kevoree.Operation
import org.kevoree.PortTypeMapping
import org.kevoree.PortTypeRef
import org.kevoree.ServicePortType
import org.kevoree.TypeLibrary
import org.kevoree.annotation._
import org.kevoree.framework.AbstractComponentType
import org.kevoree.framework.MessagePort
import scala.collection.JavaConversions._


class ComponentTypeWorker(root : ContainerRoot, componentType : ComponentType, compilationUnit : CompilationUnit) {
  
  def synchronize {
    initCompilationUnit
    syncronizePackage
    var td = sychronizeClass
    synchronizeStart(td)
    synchronizeStop(td)
    synchronizeUpdate(td)
    synchronizeProvidedPorts(td)
    synchronizeRequiredPorts(td)
    synchronizeLibrary(td)
    synchronizeDictionary(td)
  }
  
  
  private def initCompilationUnit {
    if(compilationUnit.getComments == null) {
      compilationUnit.setComments(new ArrayList[Comment])
    }
    
    if(compilationUnit.getImports == null) {
      compilationUnit.setImports(new ArrayList[ImportDeclaration])
    }
    
    if(compilationUnit.getTypes == null) {
      compilationUnit.setTypes(new ArrayList[TypeDeclaration])
    }
  }
  
  
  private def syncronizePackage {
    if(compilationUnit.getPackage == null) {
      var packDec = new PackageDeclaration(new NameExpr(componentType.getBean.substring(0,componentType.getBean.lastIndexOf("."))));
      compilationUnit.setPackage(packDec)      
    }  
  }
  
  
  private def sychronizeClass : TypeDeclaration = {
   
    var td = compilationUnit.getTypes.find({typ => typ.getName.equals(componentType.getName)}) match {
      case Some(td:TypeDeclaration) => td
      case None => {
          //Class creation
          var classDecl = new ClassOrInterfaceDeclaration(ModifierSet.PUBLIC, false, componentType.getName);
          classDecl.setAnnotations(new ArrayList[AnnotationExpr])
          classDecl.setMembers(new ArrayList[BodyDeclaration])
          ASTHelper.addTypeDeclaration(compilationUnit,classDecl)

          classDecl.setExtends(new ArrayList[ClassOrInterfaceType])
          classDecl.getExtends.add(new ClassOrInterfaceType(classOf[AbstractComponentType].getName))

          checkOrAddImport(classOf[AbstractComponentType].getName)

          classDecl
        }
    }
    
    checkOrAddMarkerAnnotation(td,classOf[org.kevoree.annotation.ComponentType].getName)
    td
  }
  
  private def synchronizeStart(td : TypeDeclaration) {
    //Check method presence
    var startMethod = new MethodDeclaration
    
    if(componentType.getStartMethod == null || componentType.getStartMethod.equals("")) {
      
      //No start method in the model
      var possibleMethodNames = List("start", "startComponent", "startKevoreeComponent")
      var methods = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]})
      
      //Check available names for start method
      var availableNames = possibleMethodNames.filter({name => 
          methods.filter({method =>
              method.asInstanceOf[MethodDeclaration].getName.equals(name)}).size == 0})
      
      
      startMethod = availableNames.head match {
        case name : String => addDefaultMethod(td, name)
        case _=> {
            printf("No name found for Start method name generation. Please add the start annotation by hand.")
            null
          }
      }
      
    } else {
      startMethod = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]}).find({method =>
          method.asInstanceOf[MethodDeclaration].getName.equals(componentType.getStartMethod)}) match {
        case Some(m:MethodDeclaration) => m
        case None=> addDefaultMethod(td, componentType.getStartMethod)
      }
    }
    
    //Check annotation
    if(startMethod != null) {
      checkOrAddMarkerAnnotation(startMethod, classOf[Start].getName)
    }
  }
 
  private def synchronizeStop(td : TypeDeclaration) {    
    //Check method presence
    var stopMethod = new MethodDeclaration
    
    if(componentType.getStopMethod == null || componentType.getStopMethod.equals("")) {
      
      //No start method in the model
      var possibleMethodNames = List("stop", "stopComponent", "stopKevoreeComponent")
      var methods = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]})
      
      //Check available names for start method
      var availableNames = possibleMethodNames.filter({name => 
          methods.filter({method =>
              method.asInstanceOf[MethodDeclaration].getName.equals(name)}).size == 0})
      
      
      stopMethod = availableNames.head match {
        case name : String => addDefaultMethod(td, name)
        case _=> {
            printf("No name found for Stop method name generation. Please add the stop annotation by hand.")
            null
          }
      }
      
    } else {
      stopMethod = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]}).find({method =>
          method.asInstanceOf[MethodDeclaration].getName.equals(componentType.getStopMethod)}) match {
        case Some(m:MethodDeclaration) => m
        case None=> addDefaultMethod(td, componentType.getStopMethod)
      }
    }
    
    //Check annotation
    if(stopMethod != null) {
      checkOrAddMarkerAnnotation(stopMethod, classOf[Stop].getName)
    }
  }

  private def synchronizeUpdate(td : TypeDeclaration) {
    //Check method presence
    var updateMethod = new MethodDeclaration

    if(componentType.getUpdateMethod == null || componentType.getUpdateMethod.equals("")) {

      //No start method in the model
      var possibleMethodNames = List("update", "updateComponent", "updateKevoreeComponent")
      var methods = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]})

      //Check available names for start method
      var availableNames = possibleMethodNames.filter({name =>
          methods.filter({method =>
              method.asInstanceOf[MethodDeclaration].getName.equals(name)}).size == 0})


      updateMethod = availableNames.head match {
        case name : String => addDefaultMethod(td, name)
        case _=> {
            printf("No name found for Update method name generation. Please add the update annotation by hand.")
            null
          }
      }

    } else {
      updateMethod = td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]}).find({method =>
          method.asInstanceOf[MethodDeclaration].getName.equals(componentType.getStopMethod)}) match {
        case Some(m:MethodDeclaration) => m
        case None=> addDefaultMethod(td, componentType.getUpdateMethod)
      }
    }

    //Check annotation
    if(updateMethod != null) {
      checkOrAddMarkerAnnotation(updateMethod, classOf[Update].getName)
    }
  }


  private def synchronizeProvidedPorts(td : TypeDeclaration) {
    
    componentType.getProvided.size match {
      case 0 => {
          //remove provided ports if exists
          checkOrRemoveAnnotation(td, classOf[Provides].getName)
          checkOrRemoveAnnotation(td, classOf[ProvidedPort].getName)
        }
      case 1 => {
          //check ProvidedPort
          checkOrAddProvidedPortAnnotation(td.getAnnotations, componentType.getProvided.head.asInstanceOf[PortTypeRef], td)
        }
      case _ => {
          //check ProvidedPorts
          checkOrAddProvidesAnnotation(td)
        }
    }
  }
  
  private def synchronizeRequiredPorts(td : TypeDeclaration) {
    
    componentType.getRequired.size match {
      case 0 => {
          //remove provided ports if exists
          checkOrRemoveAnnotation(td, classOf[Requires].getName)
          checkOrRemoveAnnotation(td, classOf[RequiredPort].getName)
        }
      case 1 => {
          //check ProvidedPort
          checkOrAddRequiredPortAnnotation(td.getAnnotations, componentType.getRequired.head.asInstanceOf[PortTypeRef], td)
        }
      case _ => {
          //check ProvidedPorts
          checkOrAddRequiresAnnotation(td)
        }
    }
  }

  private def synchronizeLibrary(td : TypeDeclaration) {
    val lib = root.getLibraries.find({libraryType =>
        libraryType.getSubTypes.find({subType => subType.getName.equals(componentType.getName)}) match {
          case Some(s) => true
          case None => false}
      }).head

    //Check Annotation
    checkOrAddLibraryAnnotation(td, lib)
  }

  private def synchronizeDictionary(td : TypeDeclaration) {

    if(componentType.getDictionaryType != null) {
      val dic : SingleMemberAnnotationExpr = td.getAnnotations.find({annot => annot.getName.toString.equals(classOf[DictionaryType].getSimpleName)}) match {
        case Some(annot) => annot.asInstanceOf[SingleMemberAnnotationExpr]
        case None => {
            val newDic = createDictionaryAnnotation
            td.getAnnotations.add(newDic)
            newDic
          }
      }
      checkOrUpdateDictionary(td, dic)
      // checkOrAddImport(classOf[Library].getName)
    }

  }

  private def checkOrUpdateDictionary(td : TypeDeclaration, dicAnnot : SingleMemberAnnotationExpr) {
    //for each attribute in the model
    componentType.getDictionaryType.getAttributes.foreach{dicAtt =>
      //retreive or create the attribute annotation
      dicAnnot.getMemberValue.asInstanceOf[ArrayInitializerExpr].getValues.find({member =>
          member.asInstanceOf[NormalAnnotationExpr].getPairs.find({pair =>
              pair.getName.equals("name") && pair.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(dicAtt.getName)}) match {
            case Some(s)=>true
            case None=>false
          }}) match {

        case Some(ann)=>updateDictionaryAtribute(ann.asInstanceOf[NormalAnnotationExpr], dicAtt)
        case None => dicAnnot.getMemberValue.asInstanceOf[ArrayInitializerExpr].getValues.add(
            createDictionaryAttributeAnnotation(componentType.getDictionaryType, dicAtt))
      }
    }
  }

  private def updateDictionaryAtribute(attributeAnn : NormalAnnotationExpr, dictAttr : org.kevoree.DictionaryAttribute) {
    //TODO
  }

  private def createDictionaryAnnotation() : SingleMemberAnnotationExpr = {
    var newAnnot = new SingleMemberAnnotationExpr(new NameExpr(classOf[DictionaryType].getSimpleName), null)
    var memberValue = new ArrayInitializerExpr
    memberValue.setValues(new ArrayList[Expression])
    newAnnot.setMemberValue(memberValue)
    checkOrAddImport(classOf[DictionaryType].getName)
    checkOrAddImport(classOf[DictionaryAttribute].getName)
    newAnnot
  }

  private def createDictionaryAttributeAnnotation(dict : org.kevoree.DictionaryType, dictAttr : org.kevoree.DictionaryAttribute) : NormalAnnotationExpr = {
    var newAnnot = new NormalAnnotationExpr(new NameExpr(classOf[DictionaryAttribute].getSimpleName), null)

    var pairs = new ArrayList[MemberValuePair]

    var portName = new MemberValuePair("name", new StringLiteralExpr(dictAttr.getName))
    pairs.add(portName)

    if(dictAttr.isOptional) {
      var opt = new MemberValuePair("optional", new BooleanLiteralExpr(dictAttr.isOptional.booleanValue))
      pairs.add(opt)
    }

    dict.getDefaultValues.find({defVal => defVal.getAttribute.getName.equals(dictAttr.getName)}) match {
      case Some(defVal) => {
          var defValue = new MemberValuePair("defaultValue", new StringLiteralExpr(defVal.getValue))
          pairs.add(defValue)
        }
      case None =>
    }

    newAnnot.setPairs(pairs)
    newAnnot
  }
  
  private def checkOrAddLibraryAnnotation(td : TypeDeclaration, lib : TypeLibrary) {
    td.getAnnotations.find({annot => annot.getName.toString.equals(classOf[Library].getSimpleName)}) match {
      case Some(annot) => {

        }
      case None => {
          td.getAnnotations.add(createLibraryAnnotation(lib))
        }
    }
    checkOrAddImport(classOf[Library].getName)
  }
  
  private def createLibraryAnnotation(lib : TypeLibrary) : NormalAnnotationExpr = {
    var newAnnot = new NormalAnnotationExpr(new NameExpr(classOf[Library].getSimpleName), null)

    var pairs = new ArrayList[MemberValuePair]

    var portName = new MemberValuePair("name", new StringLiteralExpr(lib.getName))
    pairs.add(portName)

    newAnnot.setPairs(pairs)
    newAnnot
  }

  private def checkOrAddRequiredPortAnnotation(annotList : java.util.List[AnnotationExpr], requiredPort : PortTypeRef, td : TypeDeclaration) {
    
    var annotation : NormalAnnotationExpr = annotList.filter({annot => 
        annot.getName.toString.equals(classOf[RequiredPort].getSimpleName)}).find({annot => 
        annot.asInstanceOf[NormalAnnotationExpr].getPairs.find({pair => 
            pair.getName.equals("name")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(requiredPort.getName)}) match {
      case Some(annot : NormalAnnotationExpr) => annot
      case None =>  {
          var annot = createRequiredPortAnnotation
          annotList.add(annot)
          annot
        }
    }
       
    var pairs = new ArrayList[MemberValuePair]
          
    var portName = new MemberValuePair("name", new StringLiteralExpr(requiredPort.getName))
    pairs.add(portName)
    
    checkOrAddImport(classOf[PortType].getName)
    requiredPort.getRef match {
      case portTypeRef:MessagePortType => {
          var portType = new MemberValuePair("type", new FieldAccessExpr(new NameExpr("PortType"),"MESSAGE"))
          pairs.add(portType)
          
        }
      case portTypeRef:ServicePortType => {
          var portType = new MemberValuePair("type", new FieldAccessExpr(new NameExpr("PortType"),"SERVICE"))
          pairs.add(portType)
          var serviceClass = new MemberValuePair("className", 
                                                 new FieldAccessExpr(
              new NameExpr(portTypeRef.getName.substring(portTypeRef.getName.lastIndexOf(".")+1)),
              "class") )
          pairs.add(serviceClass)
          checkOrAddImport(portTypeRef.getName)
        }        
      case _ =>
    }
    
    var optional = new MemberValuePair("optional", new BooleanLiteralExpr(requiredPort.getOptional.booleanValue))
    pairs.add(optional)
    
    annotation.setPairs(pairs)
   
  }
  
  private def checkOrAddProvidesAnnotation(td : TypeDeclaration) {
    var annotation : SingleMemberAnnotationExpr = td.getAnnotations.find({annot => 
        annot.getName.toString.equals(classOf[Provides].getSimpleName)}) match {
      case Some(annot : SingleMemberAnnotationExpr) => annot
      case None =>  {
          var annot = createProvidesAnnotation
          td.getAnnotations.add(annot)
          annot
        }
    }
    
    var providedPortAnnotationsList : java.util.List[AnnotationExpr] = if(annotation.getMemberValue == null) {
      new ArrayList[AnnotationExpr]
    } else {
      annotation.getMemberValue match {
        case arrayInitExpr : ArrayInitializerExpr => arrayInitExpr.getValues.asInstanceOf[java.util.List[AnnotationExpr]]
        case _ => new ArrayList[AnnotationExpr]
      }
    }

    componentType.getProvided.foreach { providedPort =>
      printf("Dealing with " + providedPort.getName + " ProvidedPort")
      checkOrAddProvidedPortAnnotation(providedPortAnnotationsList, providedPort, td)
    }
    
    annotation.setMemberValue(new ArrayInitializerExpr(providedPortAnnotationsList.toList))
    
  }
  
  private def checkOrAddRequiresAnnotation(td : TypeDeclaration) {
    var annotation : SingleMemberAnnotationExpr = td.getAnnotations.find({annot => 
        annot.getName.toString.equals(classOf[Requires].getSimpleName)}) match {
      case Some(annot : SingleMemberAnnotationExpr) => annot
      case None =>  {
          var annot = createRequiresAnnotation
          td.getAnnotations.add(annot)
          annot
        }
    }
    
    var requiredPortAnnotationsList : java.util.List[AnnotationExpr] = if(annotation.getMemberValue == null) {
      new ArrayList[AnnotationExpr]
    } else {
      annotation.getMemberValue match {
        case arrayInitExpr : ArrayInitializerExpr => arrayInitExpr.getValues.asInstanceOf[java.util.List[AnnotationExpr]]
        case _ => new ArrayList[AnnotationExpr]
      }
    }

    componentType.getRequired.foreach { requiredPort =>
      printf("Dealing with " + requiredPort.getName + " RequiredPort")
      checkOrAddRequiredPortAnnotation(requiredPortAnnotationsList, requiredPort, td)
    }
    
    annotation.setMemberValue(new ArrayInitializerExpr(requiredPortAnnotationsList.toList))
    
  }
  
  private def checkOrAddProvidedPortAnnotation(annotList : java.util.List[AnnotationExpr], providedPort : PortTypeRef, td : TypeDeclaration) {
    var annotation : NormalAnnotationExpr = annotList.filter({annot => 
        annot.getName.toString.equals(classOf[ProvidedPort].getSimpleName)}).find({annot => 
        annot.asInstanceOf[NormalAnnotationExpr].getPairs.find({pair => 
            pair.getName.equals("name")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(providedPort.getName)}) match {
      case Some(annot : NormalAnnotationExpr) => annot
      case None =>  {
          var annot = createProvidedPortAnnotation
          annotList.add(annot)
          annot
        }
    }
          
    var pairs = new ArrayList[MemberValuePair]
          
    var portName = new MemberValuePair("name", new StringLiteralExpr(providedPort.getName))
    pairs.add(portName)
    
    
    checkOrAddImport(classOf[PortType].getName)
    providedPort.getRef match {
      case portTypeRef:MessagePortType => {
          var portType = new MemberValuePair("type", new FieldAccessExpr(new NameExpr("PortType"),"MESSAGE"))
          pairs.add(portType)
          
        }
      case portTypeRef:ServicePortType => {
          var portType = new MemberValuePair("type", new FieldAccessExpr(new NameExpr("PortType"),"SERVICE"))
          pairs.add(portType)
          var serviceClass = new MemberValuePair("className", 
                                                 new FieldAccessExpr(
              new NameExpr(portTypeRef.getName.substring(portTypeRef.getName.lastIndexOf(".")+1)),
              "class") )
          pairs.add(serviceClass)
          checkOrAddImport(portTypeRef.getName)
        }        
      case _ =>
    }
    
    annotation.setPairs(pairs)
   
    checkProvidedPortMappedMethod(providedPort, td)
  }
 
  private def checkProvidedPortMappedMethod(providedPort : PortTypeRef, td : TypeDeclaration) {
    
    providedPort.getRef match {
      case srvPort : ServicePortType => {
          
          //For each operation of the service port
          srvPort.getOperations.foreach{operation =>
            
            //Search if a mapping is already present in the model for this operation
            var method : MethodDeclaration = providedPort.getMappings.find({mapping => 
                mapping.getServiceMethodName.equals(operation.getName)}) match {
              
              //Mapping present 
              case Some(mapping) => {
                  
                  //Check method existence
                  td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]}).find({method =>
                      method.asInstanceOf[MethodDeclaration].getName.equals(mapping.getBeanMethodName)}) match {
                    //Method Exists : return method
                    case Some(methodDecl) => methodDecl.asInstanceOf[MethodDeclaration]
                      //Method not found, create Method using the method name present in mapping declaration
                    case None => createProvidedServicePortMethod(operation, mapping.getBeanMethodName, td)
                  }
                }
                
                //No Mapping
              case None => {
                  //Find a method name not already used
                  var methodName = "on"
                  methodName += operation.getName.substring(0,1).capitalize
                  methodName += operation.getName.substring(1);
                  methodName += "From" + providedPort.getName.substring(0,1).capitalize
                  methodName += providedPort.getName.substring(1) + "PortActivated";
                 
                  //Add Mapping
                  var newMapping = org.kevoree.KevoreeFactory.eINSTANCE.createPortTypeMapping
                  newMapping.setServiceMethodName(operation.getName)
                  newMapping.setBeanMethodName(methodName)
                  providedPort.getMappings.add(newMapping)
                  
                  //Create the method
                  createProvidedServicePortMethod(operation, methodName, td)
                 
                }
            }
            //Check annotation on the method corresponding to the operation
            checkOrAddPortAnnotationOnMethod(providedPort, method, operation.getName)
          }
        }
      case msgPort : MessagePortType => {
          //Search if a mapping is already present in the model for this operation
          var method : MethodDeclaration = providedPort.getMappings.find({mapping => mapping.getServiceMethodName.equals("process")}) match {
            //Mapping present
            case Some(mapping) => {
                //Check method existence
                td.getMembers.filter({member => member.isInstanceOf[MethodDeclaration]}).find({method =>
                    method.asInstanceOf[MethodDeclaration].getName.equals(mapping.getBeanMethodName)}) match {
                  //Method Exists : return method
                  case Some(methodDecl) => methodDecl.asInstanceOf[MethodDeclaration]
                    //Method not found, create Method using the method name present in mapping declaration
                  case None => {
                      createProvidedMessagePortMethod(mapping.getBeanMethodName, td)
                    }
                }
              }
              //No Mapping
            case None => {
                
                //Find a method name not already used
                var methodName = providedPort.getName.substring(0,1).capitalize
                methodName += providedPort.getName.substring(1);
                 
                //Add Mapping
                var newMapping = org.kevoree.KevoreeFactory.eINSTANCE.createPortTypeMapping
                newMapping.setServiceMethodName("process")
                newMapping.setBeanMethodName(methodName)
                providedPort.getMappings.add(newMapping)
                
                //Create Method
                createProvidedMessagePortMethod(methodName, td)
              }
          }
          //Check annotation
          checkOrAddPortAnnotationOnMethod(providedPort, method, "process")
        }
      case _ =>
    }
    
  }
  
  private def checkOrAddPortAnnotationOnMethod(providedPort : PortTypeRef, method : MethodDeclaration, operationName : String) {
    
    //If method newly created, annotation list is null
    if(method.getAnnotations == null) {
      method.setAnnotations(new ArrayList[AnnotationExpr])
    }
    
    //retreive concerned mapping
    var mapping = providedPort.getMappings.find({mapping => 
        mapping.getServiceMethodName.equals(operationName)}).head

    //creates the new annotation
    var newAnnot = createPortAnnotation(providedPort, operationName)
    
    var usefullAnnots = method.getAnnotations.filter({annot => (annot.getName.toString.equals(classOf[Port].getSimpleName)||annot.getName.toString.equals(classOf[Ports].getSimpleName))})
    
    usefullAnnots.size match {
      case 0 => {
          method.getAnnotations.add(newAnnot)
          checkOrAddImport(classOf[Port].getName)
        }
      case 1 => {
          if(usefullAnnots.head.getName.toString.equals(classOf[Port].getSimpleName)) {
            checkOrAddPortMapping(method, usefullAnnots.head.asInstanceOf[NormalAnnotationExpr], newAnnot, providedPort, operationName)            
          } else if(usefullAnnots.head.getName.toString.equals(classOf[Ports].getSimpleName)) {
            checkOrAddPortMappingAnnotationToPortsAnnotation(usefullAnnots.head.asInstanceOf[SingleMemberAnnotationExpr], newAnnot, providedPort, operationName)
          } else {
            method.getAnnotations.add(newAnnot)
            checkOrAddImport(classOf[Port].getName)
          }
        }
      case _ => { //NOT POSSIBLE 
          printf("ERROR")
          /*
           method.getAnnotations.find({annot => annot.asInstanceOf[NormalAnnotationExpr].getName.toString.equals(classOf[Ports].getSimpleName)}) match {
           case Some(portsAnnot : SingleMemberAnnotationExpr) => {
           checkOrAddPortMappingAnnotationToPortsAnnotation(portsAnnot, newAnnot, providedPort, operationName)
           }
           case None => {
           method.getAnnotations.find({annot => annot.asInstanceOf[NormalAnnotationExpr].getName.toString.equals(classOf[Port].getSimpleName)}) match {
           case Some(annot : NormalAnnotationExpr) => {
           changeAndAdd_PortToPorts(method, method.getAnnotations.head.asInstanceOf[NormalAnnotationExpr], newAnnot, providedPort, operationName)
           }
           case None => {
           method.getAnnotations.add(newAnnot)
           checkOrAddImport(classOf[Port].getName)
           }
           }
           }
           }*/
        }
    }
  }
  
  private def createPortAnnotation(providedPort : PortTypeRef, operationName : String) = {
    var pairs = new ArrayList[MemberValuePair]
          
    var portName = new MemberValuePair("name", new StringLiteralExpr(providedPort.getName))
    pairs.add(portName)
    
    var methName = new MemberValuePair("method", new StringLiteralExpr(operationName))
    pairs.add(methName)
                  
    new NormalAnnotationExpr(new NameExpr(classOf[Port].getSimpleName), pairs)
  }
  
  private def checkOrAddPortMapping(method : MethodDeclaration, portAnnot : NormalAnnotationExpr, newAnnot : NormalAnnotationExpr, providedPort : PortTypeRef, operationName : String) {
    var portAnnot = method.getAnnotations.head.asInstanceOf[NormalAnnotationExpr]
    
    providedPort.getRef match {
      case srv : ServicePortType =>  {
          if( !portAnnot.getPairs.find({pair => pair.getName.equals("name")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(providedPort.getName) ||
             !portAnnot.getPairs.find({pair => pair.getName.equals("method")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(operationName) ) {
            changeAndAdd_PortToPorts(method, portAnnot, newAnnot)
          }
        }
      case msg : MessagePortType => {
          if( !portAnnot.getPairs.find({pair => pair.getName.equals("name")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(providedPort.getName) ) {
            changeAndAdd_PortToPorts(method, portAnnot, newAnnot)
          }
        }
      case _ =>
    }
  }
  
  private def changeAndAdd_PortToPorts(method : MethodDeclaration, portAnnot : NormalAnnotationExpr, newAnnot : NormalAnnotationExpr) {
       
    var portsAnnot = new SingleMemberAnnotationExpr(new NameExpr(classOf[Ports].getSimpleName), null)
    var memberValue = new ArrayInitializerExpr
    memberValue.setValues(new ArrayList[Expression])
    memberValue.getValues.add(portAnnot)
    memberValue.getValues.add(newAnnot)
    portsAnnot.setMemberValue(memberValue)
            
    method.getAnnotations.remove(portAnnot)
    method.getAnnotations.add(portsAnnot)
    checkOrAddImport(classOf[Ports].getName)
    
  }
  
  private def checkOrAddPortMappingAnnotationToPortsAnnotation(portsAnnotation : SingleMemberAnnotationExpr, newAnnot : NormalAnnotationExpr, providedPort : PortTypeRef, operationName : String) {
    portsAnnotation.getMemberValue.asInstanceOf[ArrayInitializerExpr].getValues.find({portAnnot =>
        !portAnnot.asInstanceOf[NormalAnnotationExpr].getPairs.find({pair => pair.getName.equals("name")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(providedPort.getName) ||
        !portAnnot.asInstanceOf[NormalAnnotationExpr].getPairs.find({pair => pair.getName.equals("method")}).head.getValue.asInstanceOf[StringLiteralExpr].getValue.equals(operationName)}) match {
      case None => portsAnnotation.getMemberValue.asInstanceOf[ArrayInitializerExpr].getValues.add(newAnnot)
      case Some(s) =>
    }
  }
  
  private def createProvidedServicePortMethod(operation : Operation, methodName : String, td : TypeDeclaration) : MethodDeclaration = {
    var newMethod = new MethodDeclaration(ModifierSet.PUBLIC, ASTHelper.VOID_TYPE, methodName);
    newMethod.setType(new ClassOrInterfaceType(operation.getReturnType.getName))
                        
    //Method body block
    var block = new BlockStmt();
    newMethod.setBody(block);
           
    var parameterList = new ArrayList[Parameter]
                        
    operation.getParameters.foreach{parameter =>
      var param = new Parameter(0, 
                                new ClassOrInterfaceType(parameter.getType.toString),
                                new VariableDeclaratorId(parameter.getName))
      parameterList.add(param)
                        
    }
    newMethod.setParameters(parameterList)
    ASTHelper.addMember(td, newMethod);
    newMethod
  }
  
  private def createProvidedMessagePortMethod(methodName : String, td : TypeDeclaration) : MethodDeclaration = {
    var newMethod = new MethodDeclaration(ModifierSet.PUBLIC, ASTHelper.VOID_TYPE, methodName);
            
    //Method body block
    var block = new BlockStmt();
    newMethod.setBody(block);
           
    var parameterList = new ArrayList[Parameter]
    var param = new Parameter(0, new ClassOrInterfaceType("Object"), new VariableDeclaratorId("msg"))
    parameterList.add(param)
    newMethod.setParameters(parameterList)
           
    ASTHelper.addMember(td, newMethod);
    newMethod
  }
  
  private def createProvidedPortAnnotation : NormalAnnotationExpr = {
    var newAnnot = new NormalAnnotationExpr(new NameExpr(classOf[ProvidedPort].getSimpleName), null)
    checkOrAddImport(classOf[ProvidedPort].getName)
    newAnnot
  }
  
  private def createRequiredPortAnnotation : NormalAnnotationExpr = {
    var newAnnot = new NormalAnnotationExpr(new NameExpr(classOf[RequiredPort].getSimpleName), null)
    checkOrAddImport(classOf[RequiredPort].getName)
    newAnnot
  }
  
  private def createProvidesAnnotation : SingleMemberAnnotationExpr = {
    var newAnnot = new SingleMemberAnnotationExpr(new NameExpr(classOf[Provides].getSimpleName), null)
    checkOrAddImport(classOf[Provides].getName)
    newAnnot
  }
  
  private def createRequiresAnnotation : SingleMemberAnnotationExpr = {
    var newAnnot = new SingleMemberAnnotationExpr(new NameExpr(classOf[Requires].getSimpleName), null)
    checkOrAddImport(classOf[Requires].getName)
    newAnnot
  }
  
  private def checkOrRemoveAnnotation(declaration : BodyDeclaration, annQName : String) {
    if(declaration.getAnnotations != null) {
      var annSimpleName = annQName.substring(annQName.lastIndexOf(".")+1)
      declaration.getAnnotations.find({ann => ann.getName.toString.equals(annSimpleName)}) match {
        
        case Some(annot : NormalAnnotationExpr) => {
            
            //Remove imports of internal annotations recursively if necessary
            annot.getPairs.foreach{memberPair =>
              memberPair.getValue match {
                case internalAnnot : AnnotationExpr => //TODO
                case _ =>
              }
            }
            
            //Remove annotation
            declaration.getAnnotations.remove(annot)
            
            //Remove Import
            checkOrRemoveImport(annot.getName.toString)
          }
        case Some(annot : SingleMemberAnnotationExpr) => {
            
            //Remove member
            annot.getMemberValue match {
              case annot : AnnotationExpr =>
              case member => printf("AnnotationMember type not foreseen(" + member.getClass.getName + ")")
            }
            
            //Remove annotation
            declaration.getAnnotations.remove(annot)
            
            //Remove Import
            checkOrRemoveImport(annot.getName.toString)
          }
        case Some(annot : MarkerAnnotationExpr) => {
            //Remove annotation
            declaration.getAnnotations.remove(annot)
            
            //Remove Import
            checkOrRemoveImport(annot.getName.toString)
          }
        case None => 
      }
    }
  }
  
  private def checkOrAddMarkerAnnotation(declaration : BodyDeclaration, annQName : String) {
    if(declaration.getAnnotations == null) {
      declaration.setAnnotations(new ArrayList[AnnotationExpr])
    }
    
    val annSimpleName = annQName.substring(annQName.lastIndexOf(".")+1)
    
    declaration.getAnnotations.find({ann => ann.getName.toString.equals(annSimpleName)}) match {
      case None => {
          declaration.getAnnotations.add(new MarkerAnnotationExpr(new NameExpr(annSimpleName)))
        }
      case Some(s)=>
    }
            
    //Adding import declaration
    checkOrAddImport(annQName)
  }
  
  private def addDefaultMethod(td : TypeDeclaration, methodName : String) : MethodDeclaration = {
    //Method declaration
    var method = new MethodDeclaration(ModifierSet.PUBLIC, ASTHelper.VOID_TYPE, methodName);
            
    //Method body block
    var block = new BlockStmt();
    method.setBody(block);
           
    //TODO: add a //TODO coment in the empty start method
           
    ASTHelper.addMember(td, method);
            
    componentType.setStartMethod(methodName)
    method
  }

  private def checkOrAddImport(classQName : String) {
    compilationUnit.getImports.find({importDecl => importDecl.getName.toString.equals(classQName)}) match {
      case None => {
          compilationUnit.getImports.add(new ImportDeclaration(new NameExpr(classQName), false, false))
        }
      case Some(s)=>
    }
  }
  
  private def checkOrRemoveImport(classQName : String) {
    compilationUnit.getImports.find({importDecl => importDecl.getName.toString.equals(classQName)}) match {
      case None => //done
      case Some(s)=> {
          //check if class is still used in CU
          //remove if not
        }
    }
  }
}