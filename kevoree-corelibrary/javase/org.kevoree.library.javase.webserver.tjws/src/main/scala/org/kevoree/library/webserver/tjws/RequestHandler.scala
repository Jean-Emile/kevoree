package org.kevoree.library.webserver.tjws

import org.kevoree.library.javase.webserver.{AbstractWebServer, KevoreeHttpRequest, KevoreeHttpResponse}
import org.kevoree.framework.MessagePort
import actors.Actor
import collection.mutable
import org.kevoree.library.webserver.internal.IDGenerator

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 11/04/12
 * Time: 20:41
 */

case class CLOSE()

case class REMOVE(id: Int)

case class ENABLE(id: Int)

case class GetHandler()

class RequestHandler(origin: AbstractWebServer) extends Actor {
  def killActors() {
    this ! CLOSE()
    handlers.foreach{ handler =>
      handler ! CLOSE()
    }
  }

  var handlers = new Array[ResponseHandler](200)
  var freeIDS = new IDGenerator(200)

  def staticInit(timeout : Int) {
    val pointer = this
    for (i <- 0 until handlers.length) {
      handlers(i) = new ResponseHandler(timeout, pointer)
      handlers(i).start()
    }
  }

  def sendAndWait(rr: KevoreeHttpRequest): KevoreeHttpResponse = {
    rr.setTokenID(freeIDS.getID)
    origin.getPortByName("handler", classOf[MessagePort]).process(rr)
    handlers(rr.getTokenID).sendAndWait(rr.getTokenID)
  }

  def internalSend(resp: KevoreeHttpResponse) {
    this ! resp
  }

  def act() {
    loop {
      react {
        case msg: KevoreeHttpResponse => {
          if (msg.getTokenID >= 0 && msg.getTokenID < handlers.size) {
            handlers(msg.getTokenID).checkAndReply(msg)
          }
        }
        case CLOSE() => exit()
      }
    }
  }
}
