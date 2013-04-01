package org.kevoree.library.arduinoNodeType

import org.kevoree.api.service.core.checker.{CheckerViolation, CheckerService}
import org.kevoree.{Instance, ContainerRoot}
import org.kevoree.framework.kaspects.ChannelAspect
import java.util.ArrayList
import scala.collection.JavaConversions._

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 17/02/12
 * Time: 15:56
 */

class ArduinoChecker(nodeName: String) extends CheckerService {

  private val channelAspect = new ChannelAspect()

  def check(model: ContainerRoot): java.util.List[CheckerViolation] = {
    val result: java.util.List[CheckerViolation] = new ArrayList[CheckerViolation]()
    model.getNodes.find(n => n.getName == nodeName) match {
      case Some(selfNode) => {
        var instanceRelated : List[Instance] = List[Instance]()
        selfNode.getComponents.foreach {
          c =>
            instanceRelated = instanceRelated ++ List(c)
        }
        model.getHubs.foreach {
          channel =>
            if (channelAspect.getRelatedNodes(channel).exists(n => n.getName == nodeName)) {
              instanceRelated = instanceRelated ++ List(channel)
            }
        }
        //TODO GROUPS
        instanceRelated.foreach {
          instanceRelated =>
          //CHECK NAME
            if (instanceRelated.getName.size > 3) {
              val violation = new CheckerViolation
              violation.setMessage("Instance name "+instanceRelated.getName+" to long for arduino node")
              val errorObbj = new ArrayList[Object]()
              errorObbj.add(instanceRelated)
              violation.setTargetObjects(errorObbj)
              result.add(violation)
            }
        }
      }
      case None => {
        val violation = new CheckerViolation
        violation.setMessage("Self Arduino Node not found")
        result.add(violation)
      }
    }
    result
  }
}
