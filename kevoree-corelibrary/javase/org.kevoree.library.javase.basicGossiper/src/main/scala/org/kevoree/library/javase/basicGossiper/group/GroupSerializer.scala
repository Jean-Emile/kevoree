package org.kevoree.library.javase.basicGossiper.group

import org.kevoree.ContainerRoot
import org.kevoree.framework.KevoreeXmiHelper
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import org.slf4j.LoggerFactory
import org.kevoree.api.service.core.handler.KevoreeModelHandlerService
import org.kevoree.library.javase.basicGossiper.Serializer

/**
 * User: Erwan Daubert
 * Date: 05/04/11
 * Time: 14:40
 */

class GroupSerializer (modelService: KevoreeModelHandlerService) extends Serializer {

  private val logger = LoggerFactory.getLogger (classOf[GroupSerializer])

  def serialize (data: Any): Array[Byte] = {
    try {
        stringFromModel (data.asInstanceOf[ContainerRoot])
    } catch {
      case e => {
        logger.error ("Model cannot be serialized: ", e)
        null
      }
    }
  }

  def deserialize (data: Array[Byte]): Any = {
    try {
      modelFromString (data)
    } catch {
      case e => {
        logger.error ("Model cannot be deserialized: ", e)
        null
      }
    }
  }

  private def modelFromString (model: Array[Byte]): ContainerRoot = {
    val stream = new ByteArrayInputStream (model)
    KevoreeXmiHelper.loadCompressedStream(stream)
  }

  private def stringFromModel (model: ContainerRoot) : Array[Byte] = {
    val out = new ByteArrayOutputStream
    KevoreeXmiHelper.saveCompressedStream(out, model)
    out.flush ()
    val bytes  = out.toByteArray
    out.close ()
    //lastSerialization = new Date(System.currentTimeMillis)
    bytes
  }
}