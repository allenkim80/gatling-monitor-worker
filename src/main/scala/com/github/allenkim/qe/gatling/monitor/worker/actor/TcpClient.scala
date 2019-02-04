package com.github.allenkim.qe.gatling.monitor.worker.actor

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString

object TcpClient {
  def props(remote: InetSocketAddress) = Props(new TcpClient(remote))
}

class TcpClient(remote: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  val manager = IO(Tcp)
  var tcpListener:ActorRef = _

  manager ! Connect(remote)

  override def preStart() = {
    super.preStart()
    tcpListener = context.actorOf(TcpListener.props(), "tcpListener")
  }

  override def receive: Receive = {
    case CommandFailed(_: Connect) ⇒
      tcpListener ! "connect failed"
      context stop self

    case c @ Connected(remote, local) ⇒
      tcpListener ! c
      val connection = sender()
      connection ! Register(self)
      tcpListener ! "Connected"
      context.become(transfer(connection))
      log.info("[[[ Tcp Connected ]]] {}", remote)
  }

  def transfer(connection: ActorRef): Receive = {
    case data: ByteString =>
      connection ! Write(data)
      log.debug("[[[ Tcp write data ]]] {}", data)
    case CommandFailed(w: Write) =>
      tcpListener ! "write failed"
      log.error("[[[ Tcp write failed ]]]")
    case Received(data) =>
      tcpListener ! data
    case "close" =>
      connection ! Close
      log.info("[[[ Tcp Connection close requested ]]]")
    case _: ConnectionClosed =>
      tcpListener ! "connection closed"
      log.info("[[[ Tcp Connection closed ]]]")
      context stop self
  }
}
