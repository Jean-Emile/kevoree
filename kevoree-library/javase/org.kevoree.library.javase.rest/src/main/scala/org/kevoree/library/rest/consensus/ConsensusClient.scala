package org.kevoree.library.rest.consensus

import org.slf4j.LoggerFactory
import org.kevoree.{ContainerRoot, Group}
import cc.spray.can._
import akka.config.Supervision._
import HttpClient._
import akka.config.Supervision.SupervisorConfig
import akka.actor.{PoisonPill, Supervisor, Actor}
import org.kevoree.framework.{KevoreeXmiHelper, Constants, KevoreePropertyHelper}


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 13/02/12
 * Time: 19:09
 *
 * @author Erwan Daubert
 * @version 1.0
 */

class ConsensusClient(groupName : String) {
  private val logger = LoggerFactory.getLogger(getClass)
  private var alreadyInitialize = 0
  var supervisorRef: Supervisor = _
  var id = ""

  def initialize () {
    if (alreadyInitialize == 0) {
      id = "kevoree.rest.group.spray-service.consensus." + groupName
      val config = ClientConfig(id)
      // start and supervise the HttpClient actor
      supervisorRef = Supervisor(SupervisorConfig(
                                                   OneForOneStrategy(List(classOf[Exception]), 3, 100),
                                                   List(Supervise(Actor.actorOf(new HttpClient(config)), Permanent))
                                                 )
                                )
    }
    alreadyInitialize += 1
  }

  def kill () {
    alreadyInitialize -= 1
    if (alreadyInitialize == 0) {
      try {
        Actor.registry.actors.foreach(actor => {
          if (actor.getId().contains(id)) {
            try {
              val result = actor ? PoisonPill
              result.get
            } catch {
              case e: akka.actor.ActorKilledException =>
            }
          }
        })

        try {
          val result = Actor.registry.actorFor(supervisorRef.uuid).get ? PoisonPill
          result.get
        } catch {
          case e: akka.actor.ActorKilledException =>
        }

      } catch {
        case _@e => logger.warn("Error while stopping Spray client ", e)
      }
    }
  }

  def initializeConsensus (group: Group, nodeName: String, currentModel: ContainerRoot, hash: Long) {
    group.getSubNodes.filter(n => n.getName != nodeName).foreach {
      node => {
        logger.debug("Initialize consensus by {}", node.getName)

      }
    }
  }


  def acquireRemoteLocks (group: Group, nodeName : String, currentModel: ContainerRoot, hash : Long, futureModel : ContainerRoot): Int = {
    logger.debug("Try to acquire a global lock")
    var nbLocked = 1

    group.getSubNodes.filter(n => n.getName != nodeName).foreach {
      node => {
        logger.debug("try to ask for lock to {}", node.getName)
        // ask to lock the remote nodes by notifying them we want to make an update from the currentHashedModel to the futureHashedModel
        // each remote nodes return the update they accept by sending their currentHashedModel and their futureHashedModel
        val remoteHashedModels = sendHash(group.getName, node.getName, currentModel, hash, futureModel, "/model/consensus/lock")
        // if remote hashes are equivalent to current hashes, we consider the remote node is locked
        if (hash == remoteHashedModels) {
          nbLocked += 1
        }
      }
    }

    nbLocked
  }

  def sendModel (group: Group, nodeName : String, currentModel: ContainerRoot, futureModel: ContainerRoot) {
    // send model
    group.getSubNodes.filter(n => n.getName != nodeName).foreach {
      node => {
        sendModel(group.getName, node.getName, currentModel, futureModel)
      }
    }
  }

  def unlock (group: Group, nodeName : String, model: ContainerRoot) {
    // unlock all nodes of the group
    group.getSubNodes.filter(n => n.getName != nodeName).foreach {
      node => {
        unlock(group.getName, node.getName, model)
      }
    }
  }

  def pull (group: Group, nodeName : String, model: ContainerRoot, hash : Long): Option[ContainerRoot] = {
    var hashes = List[(Long, List[String])]()

    group.getSubNodes.filter(n => n.getName != nodeName).foreach {
      node => {
        val remoteHashedModels = sendHash(group.getName, node.getName, model, hash, null, "/model/consensus/hash")
        if (hash != remoteHashedModels) {
          hashes.find(t => t._1 == remoteHashedModels) match {
            case None => hashes = hashes ++ List[(Long, List[String])]((remoteHashedModels, List[String](node.getName)))
            case Some(tuple) => {
              val names = tuple._2 ++ List[String](node.getName)
              hashes = hashes -- List[(Long, List[String])](tuple) ++ List[(Long, List[String])]((tuple._1, names))
            }
          }
        }
      }
    }

    hashes.find(t => hashes.forall(t2 => t2._1 != t._1 && t._2.size > t2._2.size)) match {
      case None => None
      case Some(tuple) => {
        // check if the global model is the current one in the local node
        if (tuple._1 != hash) {
          // if the current model is not the global model
          pull(group.getName, tuple._2(0), model)
        } else {
          None
        }
      }
    }
  }

  private def sendHash (groupName: String, nodeName: String, currentModel: ContainerRoot, hash : Long, futureModel : ContainerRoot, path: String): Long = {
    logger.debug("send hashes of the current and the future models to know if {} is agreed with this potential update.", nodeName)
    val ipOption = KevoreePropertyHelper.getStringNetworkProperty(currentModel, nodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP)
    val portOption = KevoreePropertyHelper.getIntPropertyForGroup(currentModel, groupName, "port", true, nodeName)

    if (ipOption.isDefined && portOption.isDefined) {

      // if there is a error about implicit concat, check the import package order
      val dialog = HttpClient.HttpDialog(ipOption.get, portOption.get, id)
        .send(HttpRequest(method = HttpMethods.GET, uri = path + "?hash=" + hash).withBody(KevoreeXmiHelper.saveToString(futureModel, false))).end

      dialog.get
      dialog.value match {
        case Some(Right(r: HttpResponse)) => {
          logger.debug("Response received:\n{}", r.bodyAsString)
          val hashes = r.bodyAsString.split("\n")
          var currentModel : Long = 0
          var currentModelFound = false
//          var futureModelFound = false
          hashes.foreach {
            hash =>
              val splitted = hash.split("=")
              if (splitted(0) == "currentModel") {
                currentModel = java.lang.Long.parseLong(splitted(1))/*.getBytes("UTF-8")*/
                currentModelFound = true
              }/* else if (splitted(0) == "futureModel") {
                futureModel = splitted(1).getBytes("UTF-8")
                futureModelFound = true
              }*/
              !currentModelFound /*|| !futureModelFound*/
          }
          /*(*/currentModel/*, futureModel)*/
        }
        case Some(Left(error)) =>
          logger.debug("Response received:\n{}", error.getMessage);0
        case _@e => logger.debug("Unable to send hashes\n{}", e);0
      }
    } else {
      0
    }
  }

  private def sendModel (groupName: String, nodeName: String, currentModel: ContainerRoot, futureModel: ContainerRoot): Boolean = {
    val ipOption = KevoreePropertyHelper.getStringNetworkProperty(currentModel, nodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP)
    val portOption = KevoreePropertyHelper.getIntPropertyForGroup(currentModel, groupName, "port", true, nodeName)

    if (ipOption.isDefined && portOption.isDefined) {
      val modelString = KevoreeXmiHelper.saveToString(futureModel, false)
      // if there is a error about implicit concat, check the import package order
      val dialog = HttpClient.HttpDialog(ipOption.get, portOption.get, id)
        .send(HttpRequest(method = HttpMethods.POST, uri = "/model/current").withBody(modelString)).end

      dialog.get
      dialog.value match {
        case Some(Right(r: HttpResponse)) => r.bodyAsString == "<ack nodeName=\"" + nodeName + "\" />"
        case Some(Left(error)) => false
        case _@e => false
      }
    } else {
      false
    }
  }

  private def unlock (groupName: String, nodeName: String, currentModel: ContainerRoot): Boolean = {
    val ipOption = KevoreePropertyHelper.getStringNetworkProperty(currentModel, nodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP)
    val portOption = KevoreePropertyHelper.getIntPropertyForGroup(currentModel, groupName, "port", true, nodeName)

    if (ipOption.isDefined && portOption.isDefined) {
      // if there is a error about implicit concat, check the import package order
      val dialog = HttpClient.HttpDialog(ipOption.get, portOption.get, id)
        .send(HttpRequest(method = HttpMethods.POST, uri = "/model/consensus/unlock")).end

      dialog.get
      dialog.value match {
        case Some(Right(r: HttpResponse)) => r.bodyAsString == "<unlock nodeName=\"" + nodeName + "\" />"
        case Some(Left(error)) => false
        case _@e => false
      }
    } else {
      false
    }
  }

  private def pull (groupName: String, nodeName: String, currentModel: ContainerRoot): Option[ContainerRoot] = {
    val ipOption = KevoreePropertyHelper.getStringNetworkProperty(currentModel, nodeName, Constants.KEVOREE_PLATFORM_REMOTE_NODE_IP)
    val portOption = KevoreePropertyHelper.getIntPropertyForGroup(currentModel, groupName, "port", true, nodeName)

    if (ipOption.isDefined && portOption.isDefined) {
      // if there is a error about implicit concat, check the import package order
      val dialog = HttpClient.HttpDialog(ipOption.get, portOption.get).send(HttpRequest(method = HttpMethods.GET, uri = "/model/current")).end

      dialog.get
      dialog.value match {
        case Some(Right(r: HttpResponse)) => Some(KevoreeXmiHelper.loadString(r.bodyAsString))
        case Some(Left(error)) => None
        case _@e => None
      }
    } else {
      None
    }
  }

}
