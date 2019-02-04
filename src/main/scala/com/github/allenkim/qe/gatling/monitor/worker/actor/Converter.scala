package com.github.allenkim.qe.gatling.monitor.worker.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import com.eakorea.qe.gatling.monitor.worker.constant.MessageName
import com.eakorea.qe.gatling.monitor.worker.message.MetricMap

import scala.collection.mutable

object Converter {
  def props(tcpClient: ActorRef) = Props(new Converter(tcpClient))
}

class Converter(tcpClient: ActorRef) extends Actor with ActorLogging {

  var timeStamp:Int = 0
  var metrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap[String, Int]()
  var receivedTime:Long = 0
  val TOOK_TIME = 5000

  override def receive: Receive = {
    case MessageName.Tick =>
      val time = System.currentTimeMillis() - receivedTime
      if(time < TOOK_TIME) {
        val ts = System.currentTimeMillis() / 1000
        val data = convertToByteString(metrics, ts)
        tcpClient ! data
        log.debug("[[[ Converter Tcp write data ]]] {}", data)
      } else {
        metrics = mutable.LinkedHashMap[String, Int]()
      }
    case MetricMap(metrics, timeStamp) =>
      this.metrics = metrics
      this.timeStamp = timeStamp
      receivedTime = System.currentTimeMillis()
      log.debug("[[[ Convert MetricMap ]]] {}", metrics)
  }

  private def convertToByteString(metrics:mutable.LinkedHashMap[String, Int], timeStamp: Long): ByteString = {
    var data = metrics.toSeq.sortBy(_._1).map(_.productIterator.mkString(" ")).mkString(s" $timeStamp \n")
    data += s" $timeStamp"
    log.debug("[[[ Merged metrics ]]] {}", data)
    ByteString(data)
  }
}
