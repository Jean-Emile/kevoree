package org.kevoree.library.javase.basicGossiper

import org.kevoree.library.basicGossiper.protocol.version.Version.VectorClock
import scala.collection.JavaConversions._


case class VectorClockAspect (self: VectorClock) {

  def printDebug () {
    if (self != null) {
      org.kevoree.log.Log.debug("VectorClock" + " - " + self.getEntiesCount + " - " + self.getTimestamp)
      self.getEntiesList.foreach {
        enties =>
          org.kevoree.log.Log.debug(enties.getNodeID + "-" + enties.getVersion /*+"-"+enties.getTimestamp*/)
      }
      org.kevoree.log.Log.debug("-- end vector clock --")
    } else {
      org.kevoree.log.Log.debug("vectorclock is null!")
    }
  }

}
