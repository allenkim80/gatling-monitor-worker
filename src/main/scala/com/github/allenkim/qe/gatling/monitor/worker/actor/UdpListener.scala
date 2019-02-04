package com.github.allenkim.qe.gatling.monitor.worker.actor

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Udp}
import com.eakorea.qe.gatling.monitor.worker.message.UdpMessage
import com.typesafe.config.ConfigFactory

object UdpListener {
  def props(nextActor: ActorRef) = Props(new UdpListener(nextActor))
}

class UdpListener(nextActor: ActorRef) extends Actor with ActorLogging {
  import context.system

  val config = ConfigFactory.load()
  val udpHost = config.getString("worker.udpHost")
  val udpPort = config.getInt("worker.udpPort")

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(udpHost, udpPort))

  override def receive: Receive = {
    case Udp.Bound(local) =>
      context.become(ready(sender()))
      log.info("[[[ Udp Bounded ]]] {}", local)
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      nextActor ! UdpMessage(data.utf8String, remote)
      log.debug("[[[ Udp message ]]] {}", data.utf8String)
    case Udp.Unbind  => socket ! Udp.Unbind
      log.info("[[[ Udp unbounded ]]]")
    case Udp.Unbound =>
      log.info("[[[ Udp unbounded ]]]")
      context.stop(self)
  }
}