package org.kevoree.library.sky.virtualCloud

import io.Source

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 27/09/11
 * Time: 00:01
 * To change this template use File | Settings | File Templates.
 */

object VirtualNodeHTMLHelper {

  def getNodeStreamAsHTML(manager:KevoreeNodeManager,nodeName : String,streamName : String):String={
      (  <html>
      <head>
          <link rel="stylesheet" href="http://twitter.github.com/bootstrap/1.3.0/bootstrap.min.css"/>
      </head>
      <body>
        <ul class="breadcrumb">
          <li><a href="/">Home</a> <span class="divider">/</span></li>
          <li><a href={"/nodes/"+nodeName}>{nodeName}</a> <span class="divider">/</span></li>
          <li class="active"><a href={"/nodes/"+nodeName+"/"+streamName}>{streamName}</a></li>
        </ul>
        {
          var result : List[scala.xml.Elem] = List()
          manager.getRunners.find(r => r.nodeName == nodeName)match {
            case Some(runner)=> {
               result = result ++ List(
                 <div class="alert-message block-message info">
                   {
                     streamName match {
                       case "out" => Source.fromFile(runner.getOutFile).getLines().mkString("<br />")
                       case "err" => Source.fromFile(runner.getErrFile).getLines().mkString("<br />")
                       case _ => "unknow stream"
                     }
                   }
                 </div>
               )
            }
            case None => {
               result = result ++ List(
                 <div class="alert-message block-message error">
                   <p>Node instance not hosted on this platform</p>
                 </div>
               )
            }
          }
          result
        }
      </body>
    </html>).toString
  }



  def getNodeHomeAsHTML(manager:KevoreeNodeManager,nodeName : String):String={
      (  <html>
      <head>
          <link rel="stylesheet" href="http://twitter.github.com/bootstrap/1.3.0/bootstrap.min.css"/>
      </head>
      <body>
        <ul class="breadcrumb">
          <li><a href="/">Home</a> <span class="divider">/</span></li>
          <li class="active"><a href={"/nodes/"+nodeName}>{nodeName}</a></li>
        </ul>
        {
          var result : List[scala.xml.Elem] = List()
          manager.getRunners.find(r => r.nodeName == nodeName)match {
            case Some(runner)=> {
               result = result ++ List(
                 <div class="alert-message block-message info">
                   <p><a href={"/nodes/"+nodeName+"/out"}>Output log</a></p>
                   <p><a href={"/nodes/"+nodeName+"/err"}>Error log</a></p>
                 </div>
               )
            }
            case None => {
               result = result ++ List(
                 <div class="alert-message block-message error">
                   <p>Node instance not hosted on this platform</p>
                 </div>
               )
            }
          }
          result
        }
      </body>
    </html>).toString
  }


  def exportNodeListAsHTML(manager:KevoreeNodeManager): String = {
    (<html>
      <head>
          <link rel="stylesheet" href="http://twitter.github.com/bootstrap/1.3.0/bootstrap.min.css"/>
      </head>
      <body>
        <ul class="breadcrumb">
          <li class="active">
            <a href="/">Home</a> <span class="divider">/</span>
          </li>
        </ul>

        <table class="zebra-striped">
          <thead><tr>
            <td>#</td> <td>virtual node</td>
          </tr></thead>
          <tbody>
          {
            var result : List[scala.xml.Elem] = List()
            manager.getRunners.foreach {elem =>
             result = result ++ List(
            <tr>
              <td>{manager.getRunners.indexOf(elem)}</td><td><a href={"nodes/"+elem.nodeName}>{elem.nodeName}</a></td>
            </tr>
             )
          }
            result
          }
          </tbody>
        </table>

      </body>
    </html>).toString()
  }


}