package org.kevoree.library.javase.basicGossiper

import java.net.InetSocketAddress
import java.util.UUID
import org.kevoree.library.basicGossiper.protocol.message.KevoreeMessage.Message
import org.kevoree.library.basicGossiper.protocol.gossip.Gossip._
import org.kevoree.library.basicGossiper.protocol.version.Version.{ClockEntry, VectorClock}

import scala.collection.JavaConversions._
import actors.Actor
import com.google.protobuf.ByteString
import org.kevoree.library.javase.{INetworkSender, NetworkSender}
import scala.Some
import scala.Tuple2
import java.util


class GossiperProcess(instance: GossiperComponent,
                      dataManager: DataManager, serializer: Serializer, doGarbage: Boolean) extends Actor {

  implicit def vectorDebug(vc: VectorClock) = VectorClockAspect(vc)

  var netSender: INetworkSender = new NetworkSender(this)


  case class STOP()

  case class INIT_GOSSIP(peer: String)

  case class RECEIVE_REQUEST(message: Message)

  def stop() {
    this ! STOP()
  }

  def initGossip(peer: String) {
    this ! INIT_GOSSIP(peer)
  }

  def receiveRequest(message: Message) {
    this ! RECEIVE_REQUEST(message)
  }

  private val VersionedModelClazz = classOf[VersionedModel].getName
  private val VectorClockUUIDsClazz = classOf[VectorClockUUIDs].getName
  private val UpdatedValueNotificationClazz = classOf[UpdatedValueNotification].getName
  private val UUIDDataRequestClazz = classOf[UUIDDataRequest].getName
  private val VectorClockUUIDsRequestClazz = classOf[VectorClockUUIDsRequest].getName

  def buildAddresses(nodeName : String, port : Int): util.List[InetSocketAddress] = {
    val addresses: util.List[InetSocketAddress] = new util.ArrayList[InetSocketAddress]()
    instance.getAddresses(nodeName).foreach {
      address =>
        addresses.add(new InetSocketAddress(address, port))
    }
    addresses
  }

  def buildLocalHostAddress(nodeName : String, port: Int): InetSocketAddress = {
    new InetSocketAddress("127.0.0.1", port)
  }

  private def send(nodeName : String, message : Message) {
    val port = instance.parsePortNumber(nodeName)
    val notSent = buildAddresses(nodeName, port).forall {
      address => !netSender.sendMessage(message, address)
    }
    if (notSent) {
      netSender.sendMessage(message, buildLocalHostAddress(nodeName, port))
    }
  }

  def act() {
    loop {
      react {
        case STOP() => stopInternal()
        case INIT_GOSSIP(peer) => {
          send(peer, createVectorClockUUIDsRequest())
          /*val notSent = buildAddresses(peer).forall {
            address => !netSender.sendMessage(createVectorClockUUIDsRequest(), address)
          }
          if (notSent) {
            netSender.sendMessage(createVectorClockUUIDsRequest(), buildLocalHostAddress(peer))
          }*/
        }
        case RECEIVE_REQUEST(message) => {
          message.getContentClass match {
            case VersionedModelClazz => {
              endGossipInternal(message)
            }
            case VectorClockUUIDsClazz => {
              processMetadataInternal(VectorClockUUIDs.parseFrom(message.getContent), message)
            }
            case UpdatedValueNotificationClazz => {
              org.kevoree.log.Log.debug("notification received from " + message.getDestNodeName)
              initGossip(message.getDestNodeName)
            }
            case UUIDDataRequestClazz => {
              org.kevoree.log.Log.debug("UUIDDataRequest received")
              send(message.getDestNodeName, buildData(message))
              /*val notSent = buildAddresses(message.getDestNodeName).forall {
                address => !netSender.sendMessage(buildData(message), address)
              }

              if (notSent) {
                netSender.sendMessage(buildData(message), buildLocalHostAddress(message.getDestNodeName))
              }*/

            }
            case VectorClockUUIDsRequestClazz => {
              send(message.getDestNodeName, buildVectorClockUUIDs(message))
              /*val notSent = buildAddresses(message.getDestNodeName).forall {
                address => !netSender.sendMessage(buildVectorClockUUIDs(message), address)
              }
              if (notSent) {
                netSender.sendMessage(buildVectorClockUUIDs(message), buildLocalHostAddress(message.getDestNodeName))
              }*/
            }
          }
        }
      }
    }
  }

  private def stopInternal() {
    this.exit()
  }

  private def createVectorClockUUIDsRequest(): Message = {
    val messageBuilder: Message.Builder = Message.newBuilder.setDestName(instance.getName).setDestNodeName(instance.getNodeName)
    messageBuilder.setContentClass(classOf[VectorClockUUIDsRequest].getName).setContent(VectorClockUUIDsRequest.newBuilder.build.toByteString).build()
  }

  private def processMetadataInternal(remoteVectorClockUUIDs: VectorClockUUIDs, message: Message) {
    if (remoteVectorClockUUIDs != null) {
      /* check for new uuid values*/
      remoteVectorClockUUIDs.getVectorClockUUIDsList.foreach {
        vectorClockUUID =>
          val uuid = UUID.fromString(vectorClockUUID.getUuid)

          if (dataManager.getUUIDVectorClock(uuid) == null) {
            org.kevoree.log.Log.debug("add empty local vectorClock with the uuid if it is not already defined")
            dataManager.setData(uuid, Tuple2[VectorClock, Message](VectorClock.newBuilder.setTimestamp(System.currentTimeMillis).build, Message.newBuilder().buildPartial()),
              message.getDestNodeName)
          }
      }

      var uuids = List[UUID]()
      remoteVectorClockUUIDs.getVectorClockUUIDsList.foreach {
        remoteVectorClockUUID =>
          uuids = uuids ++ List(UUID.fromString(remoteVectorClockUUID.getUuid))
      }
      dataManager.checkForGarbage(uuids, message.getDestNodeName)
    }
    //FOREACH UUIDs
    remoteVectorClockUUIDs.getVectorClockUUIDsList.foreach {
      remoteVectorClockUUID =>
        val uuid = UUID.fromString(remoteVectorClockUUID.getUuid)
        val remoteVectorClock = remoteVectorClockUUID.getVector
        dataManager.getUUIDVectorClock(uuid).printDebug()
        remoteVectorClock.printDebug()
        val occured = VersionUtils.compare(dataManager.getUUIDVectorClock(uuid), remoteVectorClock)
        occured match {
          case Occured.AFTER => {
            org.kevoree.log.Log.debug("VectorClocks comparison into GossiperRequestSender give us: AFTER")
          }
          case Occured.BEFORE => {
            org.kevoree.log.Log.debug("VectorClocks comparison into GossiperRequestSender give us: BEFORE")
            send(message.getDestNodeName, askForData(uuid))
            /*buildAddresses(message).foreach {
              address => askForData(uuid, message.getDestNodeName, address)
            }*/
          }
          case Occured.CONCURRENTLY => {
            org.kevoree.log.Log.debug("VectorClocks comparison into GossiperRequestSender give us: CONCURRENTLY")
            send(message.getDestNodeName, askForData(uuid))
            /*buildAddresses(message).foreach {
              address => askForData(uuid, message.getDestNodeName, address)
            }*/
          }
          case _ => org.kevoree.log.Log.error("unexpected match into initSecondStep")
        }
    }
  }

  private def endGossipInternal(message: Message) {
    if (message.getContentClass.equals(classOf[VersionedModel].getName)) {
      val versionedModel = VersionedModel.parseFrom(message.getContent)
      val uuid = versionedModel.getUuid
      var vectorClock = versionedModel.getVector
      val data = serializer.deserialize(versionedModel.getModel.toByteArray)
      if (data != null) {
        var sendOnLocal = false
        if (dataManager.getData(UUID.fromString(uuid)) == null) {
          sendOnLocal = true
        }
        if (dataManager.setData(UUID.fromString(uuid), (vectorClock, data), message.getDestNodeName)) {
          if (sendOnLocal) {
            instance.localNotification(data)
          }
          // UPDATE clock
          vectorClock.getEntiesList.find(p => p.getNodeID == instance.getNodeName) match {
            case Some(p) => //NOOP
            case None => {
              org.kevoree.log.Log.debug("add entries for the local node.")
              val newenties = ClockEntry.newBuilder.setNodeID(instance.getNodeName) /*.setTimestamp(System.currentTimeMillis)*/ .setVersion(1).build
              vectorClock = VectorClock.newBuilder(vectorClock).addEnties(newenties).setTimestamp(System.currentTimeMillis).build
            }
          }
        }
      }
    }
  }

  private def askForData(uuid: UUID/*, remoteNodeName: String, address: InetSocketAddress*/) : Message = {
    /*val messageBuilder: Message.Builder = */Message.newBuilder
      .setDestName(instance.getName).setDestNodeName(instance.getNodeName)
      .setContentClass(classOf[UUIDDataRequest].getName)
      .setContent(UUIDDataRequest.newBuilder.setUuid(uuid.toString).build.toByteString).build()
//    netSender.sendMessage(messageBuilder.build, address)
  }

  private def buildData(message: Message): Message = {
    val responseBuilder: Message.Builder = Message.newBuilder.setDestName(instance.getName)
      .setDestNodeName(instance.getNodeName)
    val uuidDataRequest = UUIDDataRequest.parseFrom(message.getContent)
    val data = dataManager.getData(UUID.fromString(uuidDataRequest.getUuid))
    org.kevoree.log.Log.debug("before serializing data : {}", data.toString())
    val bytes: Array[Byte] = serializer.serialize(data._2)
    org.kevoree.log.Log.debug("after serializing data")
    if (bytes != null) {
      val modelBytes = ByteString.copyFrom(bytes)
      val modelBytes2 = VersionedModel.newBuilder.setUuid(uuidDataRequest.getUuid).setVector(data._1).setModel(modelBytes).build.toByteString
      responseBuilder.setContentClass(classOf[VersionedModel].getName).setContent(modelBytes2)
      responseBuilder.build()
    } else {
      org.kevoree.log.Log.warn("Serialization failed !")
      null
    }
  }


  private def buildVectorClockUUIDs(message: Message): Message = {
    var responseBuilder: Message.Builder = Message.newBuilder.setDestName(instance.getName)
      .setDestNodeName(instance.getNodeName)
    org.kevoree.log.Log.debug("VectorClockUUIDsRequest request received")
    val uuidVectorClocks = dataManager.getUUIDVectorClocks
    org.kevoree.log.Log.debug("local uuids " + uuidVectorClocks.keySet().mkString(","))
    var vectorClockUUIDsBuilder = VectorClockUUIDs.newBuilder
    var resultMessage: Message = null
    uuidVectorClocks.keySet.foreach {
      uuid: UUID =>
        vectorClockUUIDsBuilder.addVectorClockUUIDs(VectorClockUUID.newBuilder.setUuid(uuid.toString).setVector(uuidVectorClocks.get(uuid)).build)
        if (vectorClockUUIDsBuilder.getVectorClockUUIDsCount == 1) {
          // it is possible to increase the number of vectorClockUUID on each message
          responseBuilder = Message.newBuilder.setDestName(instance.getName).setDestNodeName(instance.getNodeName)
          val modelBytes = vectorClockUUIDsBuilder.build.toByteString
          responseBuilder.setContentClass(classOf[VectorClockUUIDs].getName).setContent(modelBytes)
          org.kevoree.log.Log.debug("send vectorclock for " + uuid + " to " + message.getDestNodeName)
          resultMessage = responseBuilder.build()
          vectorClockUUIDsBuilder = VectorClockUUIDs.newBuilder
        }
    }
    if (uuidVectorClocks.size() == 0) {
      //vectorClockUUIDsBuilder.addVectorClockUUIDs(VectorClockUUID.newBuilder.build)
      // it is possible to increase the number of vectorClockUUID on each message
      responseBuilder = Message.newBuilder.setDestName(instance.getName).setDestNodeName(instance.getNodeName)
      val modelBytes = vectorClockUUIDsBuilder.build.toByteString
      responseBuilder.setContentClass(classOf[VectorClockUUIDs].getName).setContent(modelBytes)
      resultMessage = responseBuilder.build()
    }
    resultMessage
  }

  def setNetSender(netSender: INetworkSender) {
    this.netSender = netSender
  }

}