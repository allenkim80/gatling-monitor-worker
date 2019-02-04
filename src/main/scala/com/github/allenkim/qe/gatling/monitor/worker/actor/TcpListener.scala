package com.github.allenkim.qe.gatling.monitor.worker.actor

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString


object TcpListener {
  def props() = Props(new TcpListener)
}

class TcpListener extends Actor with ActorLogging {

  override def receive: Receive = {
    case data: ByteString =>
      log.debug("[[[ Listener Tcp received data ]]] {}", data.utf8String)
    case message: String =>
      log.debug("[[[ Listener Tcp Client log ]]] {}", message)
  }
}