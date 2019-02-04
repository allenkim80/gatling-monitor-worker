package com.github.allenkim.qe.gatling.monitor.worker.actor

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.eakorea.qe.gatling.monitor.worker.constant.{MessageName, MetricPattern}
import com.eakorea.qe.gatling.monitor.worker.message.{HostMetricMap, UdpMessage}
import com.eakorea.qe.gatling.monitor.worker.utils.MetricUtil

import scala.collection.mutable

object Accumulator {
  def props(aggregator:ActorRef) = Props(new Accumulator(aggregator))
}

class Accumulator(aggregator:ActorRef) extends Actor with ActorLogging {
  var wholeMetrics:mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, Int]] = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, Int]]
  var wholeAverages:mutable.LinkedHashMap[String, List[Int]] = mutable.LinkedHashMap.empty[String, List[Int]]
  var wholePercentiles:mutable.LinkedHashMap[String, List[Int]] = mutable.LinkedHashMap.empty[String, List[Int]]
  var timeStamp:Int = 0


  override def receive: Receive = {
    case UdpMessage(data, remote) =>
      val startTime = System.currentTimeMillis()
      accumulateMetrics(data, remote)
      log.debug("[[[ Accumulator merge time 1 ]]] {}", System.currentTimeMillis() - startTime)
      log.debug("[[[ Client metric ]]] {}", data)
    case MessageName.Tick =>
      if (wholeMetrics.nonEmpty) {
        aggregator ! HostMetricMap(wholeMetrics, wholeAverages, wholePercentiles, timeStamp)

        wholeMetrics.clear()
        wholeAverages.clear()
        wholePercentiles.clear()
      }
  }

  private def accumulateMetrics(source:String, remote:InetSocketAddress) = {
    val hostAddress = remote.getAddress.getHostAddress
    var hostMetrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap.empty[String, Int]

    source.split("\\n").foreach(line => {
      val lineArray = line.split(" ")
      val metricName = lineArray(0)
      val value = lineArray(1).toInt
      timeStamp = lineArray(2).toInt

      wholeMetrics match {
        case metrics if metrics.get(hostAddress).isDefined =>
          hostMetrics = MetricUtil.calculateMetric(metrics(hostAddress), metricName, value)
        case _ => hostMetrics += (metricName -> value)
      }

      metricName match {
        case name if name.contains(MetricPattern.PERCENTILE_METRIC_PATTERN) =>
          val percentileName = metricName.dropRight(2)
          val mergedList = wholePercentiles.getOrElse(percentileName, List()) ++ List(value)
          wholePercentiles += (percentileName -> mergedList)
        case name if name.contains(MetricPattern.AVERAGE_METRIC_PATTERN) =>
          val mergedList = wholeAverages.getOrElse(metricName, List()) ++ List(value)
          wholeAverages += (metricName -> mergedList)
        case _ => /* Do nothing */
      }
    })

    wholeMetrics.put(hostAddress, hostMetrics)
  }
}

