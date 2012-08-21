package org.kevoree.library.javase.webserver

import util.matching.Regex
import java.util.HashMap
import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import java.util

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 14/10/11
 * Time: 09:04
 * To change this template use File | Settings | File Templates.
 */

class URLHandlerScala {
  val logger = LoggerFactory.getLogger(this.getClass)
  var LocalURLPattern = new Regex("/")
  var paramNames = List[String]()

  def initRegex(pattern: String) {
    paramNames = List()
    val m = Pattern.compile("\\{(\\w+)\\}").matcher(pattern)
    val sb = new StringBuffer();
    val rsb = new StringBuffer()
    while (m.find) {
      paramNames = paramNames ++ List(m.group(1))
      rsb.replace(0, rsb.length, m.group(1))
      m.appendReplacement(sb, "(\\\\w+)")
    }
    m.appendTail(sb)
    val regexText = sb.toString.replaceAll("\\*{2,}",".*").replaceAll("[^.]\\*{1,}","/?[^/]*")
    LocalURLPattern = new Regex(regexText)
  }

  def precheck(url : String) : Boolean={
    LocalURLPattern.unapplySeq(url) match {
      case Some(paramsList) => {
        true
      }
      case _ => {
        false
      }
    }
  }

  def check(url: Any): Option[KevoreeHttpRequest] = {
    
    if(logger.isDebugEnabled){
      logger.debug("Try to check => "+LocalURLPattern.toString()+" - "+url)
    }
    
    url match {
      case request: KevoreeHttpRequest => {
        LocalURLPattern.unapplySeq(request.getUrl) match {
          case Some(paramsList) => {
            val params = new util.HashMap[String, String]
            params.putAll(request.getResolvedParams)
            var i = 0
            paramsList.foreach{ param =>
              params.put(paramNames(i),param)
              i = i +1
            }
            request.setResolvedParams(params)
            Some(request)
          }
          case _ => {
            logger.debug("Bad Pattern " + request.getUrl)
            None
          }
        }
      }
      case _ => None
    }
  }
  
  def getLastParam(url : String, urlPattern : String) : Option[String] = {

    var purlPattern = urlPattern
    val m = Pattern.compile("\\{(\\w+)\\}").matcher(purlPattern)
    while (m.find) {
      purlPattern = purlPattern.replace("{"+m.group(1)+"}",".*")
    }

    val regexText = purlPattern.toString.replaceAll("\\*{2,}","(.*)").replaceAll("[^.]\\*{1,}","(/?[^/]*)")
    (new Regex(regexText)).unapplySeq(url) match {
      case Some(l)=> {
        if(l.length > 0 ){
          Some(l.last)
        } else {
          None
        }
      }
      case _ => None
    }
  }
  
}