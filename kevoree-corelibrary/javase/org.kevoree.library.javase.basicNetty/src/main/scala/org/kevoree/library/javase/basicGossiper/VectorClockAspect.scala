package org.kevoree.library.javase.basicGossiper

import org.kevoree.library.basicGossiper.protocol.version.Version.VectorClock
import scala.collection.JavaConversions._


case class VectorClockAspect (self: VectorClock) {

  private val logger = org.slf4j.LoggerFactory.getLogger(classOf[VectorClockAspect])

  def printDebug () {
    if (self != null) {
      logger.debug("VectorClock" + " - " + self.getEntiesCount + " - " + self.getTimestamp)
      self.getEntiesList.foreach {
        enties =>
          logger.debug(enties.getNodeID + "-" + enties.getVersion /*+"-"+enties.getTimestamp*/)
      }
      logger.debug("-- end vector clock --")
    } else {
      logger.debug("vectorclock is null!")
    }
  }

}
