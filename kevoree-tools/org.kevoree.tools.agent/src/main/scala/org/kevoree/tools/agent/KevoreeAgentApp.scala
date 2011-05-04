/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kevoree.tools.agent

import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest}
import java.net.InetSocketAddress
import com.twitter.finagle.builder.{Http, ServerBuilder, Server}
import org.kevoree.tools.agent.HttpServer.Respond

object KevoreeAgentApp extends Application {

  val agent = new KevoreeRuntimeAgent



  val respond = new Respond(agent)

    // compose the Filters and Service together:
    val myService: Service[HttpRequest, HttpResponse] = /*handleExceptions andThen authorize andThen */ respond

    val server: Server = ServerBuilder()
      .codec(Http)
      .bindTo(new InetSocketAddress(8080))
      .build(myService)

    println("Kevoree Agent started !")

    println("press q to quit !")



  while(System.in.read != 'q'){
    //NOOP
  }

  println("Kill all runners")

  KevoreeNodeRunnerHandler.closeAllRunners()

  server.close()



}