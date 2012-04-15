package org.kevoree.library.javase.gossiperNetty

import java.net.InetSocketAddress
import org.kevoree.library.gossiperNetty.protocol.message.KevoreeMessage.Message
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import org.jboss.netty.handler.codec.compression.{ZlibEncoder, ZlibWrapper, ZlibDecoder}
import org.jboss.netty.handler.codec.protobuf.{ProtobufEncoder, ProtobufVarint32LengthFieldPrepender, ProtobufDecoder, ProtobufVarint32FrameDecoder}
import org.jboss.netty.channel._
import socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}
import org.jboss.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import scala.collection.JavaConversions._
import org.jboss.netty.channel.group.{ChannelGroupFutureListener, DefaultChannelGroup}

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 12/09/11
 * Time: 10:26
 */

class TCPActor (port: Int, processValue: ProcessValue, processRequest: ProcessRequest) extends NetworkActor {
  private val logger = LoggerFactory.getLogger(classOf[TCPActor])

  // configure the server
  val factoryServer = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
  val bootstrapServer = new ServerBootstrap(factoryServer)
  bootstrapServer.setPipelineFactory(new ChannelPipelineFactory() {
    override def getPipeline: ChannelPipeline = {
      val p: ChannelPipeline = Channels.pipeline()
      p.addLast("inflater", new ZlibDecoder(ZlibWrapper.ZLIB))
      p.addLast("frameDecoder", new ProtobufVarint32FrameDecoder)
      p.addLast("protobufDecoder", new ProtobufDecoder(Message.getDefaultInstance))
      p.addLast("deflater", new ZlibEncoder(ZlibWrapper.ZLIB))
      p.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender)
      p.addLast("protobufEncoder", new ProtobufEncoder)
      p.addLast("handler", new TCPRequestHandler(processRequest))
      p
    }
  })
  bootstrapServer.setOption("tcpNoDelay", true)
  val channelServer: Channel = bootstrapServer.bind(new InetSocketAddress(port))
  val serverChannelGroup = new DefaultChannelGroup("TCP-server")

  // Configure the client.
  val factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
  val bootstrap = new ClientBootstrap(factory)
  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
    override def getPipeline: ChannelPipeline = {
      val p: ChannelPipeline = Channels.pipeline()
      p.addLast("inflater", new ZlibDecoder(ZlibWrapper.ZLIB))
      p.addLast("frameDecoder", new ProtobufVarint32FrameDecoder)
      p.addLast("protobufDecoder", new ProtobufDecoder(Message.getDefaultInstance))
      p.addLast("deflater", new ZlibEncoder(ZlibWrapper.ZLIB))
      p.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender)
      p.addLast("protobufEncoder", new ProtobufEncoder)
      p.addLast("handler", new TCPValueHandler(processValue))
      p
    }
  });

  // keep all created channels to delete it when we stop
  // we only keep 10 channels, if a eleventh channel is created, we release the fifth first.
  val channelGroup = new DefaultChannelGroup("TCP-client")


  protected def stopInternal () {
//    logger.debug("stopping TCP Gossip manager")
//    logger.debug("stopping channel server")
    channelServer.close().awaitUninterruptibly()
//    logger.debug("channel server stopped")
//    logger.debug("stopping channel group")
    channelGroup.close().awaitUninterruptibly()
    serverChannelGroup.close().awaitUninterruptibly()
//    logger.debug("channel group stopped")
    // Shut down all thread pools to exit.
//    logger.debug("releasing netty bootstrap resource")
    factory.releaseExternalResources()
    bootstrap.releaseExternalResources()
//    logger.debug("netty bootstrap resource released")
    logger.debug("releasing netty bootstrap server resource")
    factoryServer.releaseExternalResources()
    logger.debug("factory server released")
    bootstrapServer.releaseExternalResources()
    logger.debug("netty bootstrap server resource released")
    logger.debug("TCP Gossip manager stopped")
  }

  protected def sendMessageInternal (o: Message, address: InetSocketAddress) {
    val future = bootstrap.connect(address)
    // Wait until the connection attempt succeeds or fails.
    val channel = future.awaitUninterruptibly().getChannel
    if (!future.isSuccess) {
      logger.error(address + "is not available", future.getCause.printStackTrace())
    } else {
      channel.write(o)
      if (channelGroup.size() == 10) {
        channelGroup.foreach {
          channel => {
            channel.close().addListener(ChannelFutureListener.CLOSE)
            logger.debug("releasing too old channel ...")
          }
        }
        channelGroup.clear()
      }
      channelGroup.add(channel);
    }
  }

  protected def sendMessageToChannelInternal (o: Message, channel: Channel, address: InetSocketAddress) {
    channel.write(o) /*.addListener(new ChannelFutureListener() {
      def operationComplete (future: ChannelFuture) {
        channel.close()
      }
    });*/
  }

  private class TCPRequestHandler (processRequest: ProcessRequest) extends SimpleChannelUpstreamHandler {
    override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent) {
      if (e.getMessage.isInstanceOf[Message]) {
        processRequest.receiveRequest(e.getMessage.asInstanceOf[Message], e.getChannel,
                                       e.getRemoteAddress.asInstanceOf[InetSocketAddress])
      }
      serverChannelGroup.add(e.getChannel)
      //e.getChannel.getCloseFuture.addListener(ChannelFutureListener.CLOSE)
    }

    override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent) {
      logger.error("Communication failed between " + ctx.getChannel.getLocalAddress + " and " +
        ctx.getChannel.getRemoteAddress, e.getCause)
      e.getChannel.close()
    }
  }

  private class TCPValueHandler (processValue: ProcessValue) extends SimpleChannelUpstreamHandler {
    override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent) {
      if (e.getMessage.isInstanceOf[Message]) {
        processValue.receiveValue(e.getMessage.asInstanceOf[Message])
      }
      //e.getChannel.close()
    }

    override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent) {
      logger.error("Communication failed between " + ctx.getChannel.getLocalAddress + " and " +
        ctx.getChannel.getRemoteAddress, e.getCause)
      e.getChannel.close()
    }
  }

}