package org.kevoree.library.javase.basicGossiper.channel

import scala.collection.JavaConversions._
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import java.lang.Math
import org.kevoree.ContainerNode
import collection.mutable
import org.kevoree.library.javase.basicGossiper.PeerSelector

class ChannelScorePeerSelector (timeout: Long, modelHandlerService: KevoreeModelHandlerService, nodeName: String)
  extends PeerSelector {

  private val peerCheckMap = new mutable.HashMap[String, (Long, Int)]
  private val peerNbFailure = new mutable.HashMap[String, Int]


  case class MODIFY_NODE_SCORE (nodeName1: String, failure: Boolean)

  case class RESET_NODE_FAILURE (nodeName1: String)

  case class SELECT_PEER (groupName: String)

  case class RESET_ALL ()

  def updateNodeScore (nodeName1: String, failure: Boolean) {
    modifyNodeScore(nodeName1, failure)
  }

  def resetAll () {
    reset();
  }

  def resetNodeFailureManagement (nodeName1: String) {
    resetNodeFailure(nodeName1)
  }

  def selectPeer (groupName: String): String = {
    selectPeerInternal(groupName)
  }

  private def selectPeerInternal (name: String): String = {
    val model = modelHandlerService.getLastModel

    model.getHubs.find(channel => channel.getName == name) match {
      case Some(channel) => {
        //Found minima score node name
        var foundNodeName = List[String]()
        var minScore = Long.MaxValue

        model.getMBindings.filter(b => b.getHub.getName.equals(channel.getName))
          .filter(b => !b.getPort.eContainer.eContainer.asInstanceOf[ContainerNode].getName.equals(nodeName))
          .foreach{
          binding => {
            val subNode = binding.getPort.eContainer.eContainer.asInstanceOf[ContainerNode]
            if (getScore(subNode.getName) <= minScore) {
              minScore = getScore(subNode.getName)
            }
          }
        }
        model.getMBindings.filter(b => b.getHub.getName.equals(channel.getName))
          .filter(b => !b.getPort.eContainer.eContainer.asInstanceOf[ContainerNode].getName.equals(nodeName))
          .foreach{
          binding => {
            val subNode = binding.getPort.eContainer.eContainer.asInstanceOf[ContainerNode]
            if (getScore(subNode.getName) == minScore) {
              foundNodeName = foundNodeName ++ List(subNode.getName)
            }
          }
        }
        if (foundNodeName.size > 0) {
        // select randomly a peer between all potential available nodes which have a good score
        val nodeName1 = foundNodeName.get((Math.random() * foundNodeName.size).asInstanceOf[Int])

        //Init node score
        //initNodeScore(nodeName)
        modifyNodeScore(nodeName1, failure = false)


          org.kevoree.log.Log.debug("return a peer between connected nodes: " + nodeName1)
        nodeName1
        } else {
          ""
        }
      }
      case None => org.kevoree.log.Log.debug(name + " not Found"); ""
    }
  }

  private def getScore (nodeName: String): Long = {
    peerCheckMap.get(nodeName) match {
      case Some(nodeTuple) => nodeTuple._1
      case None => 0l //default
    }
  }

  /*private def initNodeScore(nodeName: String) {
    peerCheckMap.get(nodeName) match {
      case Some(nodeTuple) => {
            peerCheckMap.put(nodeName,Tuple2(System.currentTimeMillis,nodeTuple._2+1))
      }
      case None => peerCheckMap.put(nodeName,Tuple2(System.currentTimeMillis,0))
    }
  }*/

  private def modifyNodeScore (nodeName: String, failure: Boolean) {
    if (failure) {
      org.kevoree.log.Log.debug("increase node score of " + nodeName + " due to communication failure")
      peerNbFailure.get(nodeName) match {
        case Some(nodeTuple) => {
          peerNbFailure.put(nodeName, nodeTuple + 1)
          peerCheckMap.get(nodeName) match {
            case Some(nodeTuple1) => {
              peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, nodeTuple1._2 + 2 * (nodeTuple + 1)))
              org.kevoree.log.Log.debug("Node score of " + nodeName + " is now " + nodeTuple + 2 * (nodeTuple + 1))
            }
            case None => peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, 2)) // must not appear
          }
        }
        case None => {
          peerNbFailure.put(nodeName, 2)
          peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, 2))
        }
      }
    } else {
      peerCheckMap.get(nodeName) match {
        case Some(nodeTuple) => {
          peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, nodeTuple._2 + 1))
        }
        case None => peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, 0))
      }
    }
  }

  private def resetNodeFailure (nodeName: String) {
    peerNbFailure.get(nodeName) match {
      case Some(nodeTuple) => {
        peerNbFailure.put(nodeName, 0)
      }
      case None => {
        peerNbFailure.put(nodeName, 0)
      }
    }
  }

  private def reset () {
    peerCheckMap.keySet.foreach {
      nodeName =>
        peerCheckMap.put(nodeName, Tuple2(System.currentTimeMillis, 0))
        peerNbFailure.put(nodeName, 0)
        org.kevoree.log.Log.debug("spam to say that scores are reinitiliaze")
    }
  }

}