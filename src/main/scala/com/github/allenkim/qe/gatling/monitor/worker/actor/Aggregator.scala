package com.github.allenkim.qe.gatling.monitor.worker.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.eakorea.qe.gatling.monitor.worker.Worker.system
import com.eakorea.qe.gatling.monitor.worker.constant.MessageName
import com.eakorea.qe.gatling.monitor.worker.message.{HostMetricMap, MetricMap}
import com.eakorea.qe.gatling.monitor.worker.utils.MetricUtil
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.duration._

object Aggregator {
  def props(tcpClient:ActorRef) = Props(new Aggregator(tcpClient))
}

class Aggregator(tcpClient:ActorRef) extends Actor with ActorLogging {
  import system.dispatcher

  val config = ConfigFactory.load()
  val accumulatorScheduleTime = config.getInt("worker.accumulatorScheduleTime")
  val aggregatorScheduleTime = config.getInt("worker.aggregatorScheduleTime")
  val converterScheduleTime = config.getInt("worker.converterScheduleTime")
  var wholeMetrics:mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, Int]] = mutable.LinkedHashMap.empty[String, mutable.LinkedHashMap[String, Int]]
  var wholeAverages:mutable.LinkedHashMap[String, List[Int]] = mutable.LinkedHashMap.empty[String, List[Int]]
  var wholePercentiles:mutable.LinkedHashMap[String, List[Int]] = mutable.LinkedHashMap.empty[String, List[Int]]
  var timeStamp:Int = 0
  var converter:ActorRef = _

  override def preStart(): Unit = {
    super.preStart()

    converter = system.actorOf(Converter.props(tcpClient), "converter")
    system.scheduler.schedule(
      (accumulatorScheduleTime + aggregatorScheduleTime) seconds,
      converterScheduleTime seconds,
      converter,
      MessageName.Tick
    )
  }

  override def receive: Receive = {
    case MessageName.Tick =>
      if (wholeMetrics.nonEmpty) {
        val startTime = System.currentTimeMillis()
        var mergedMetrics = MetricUtil.mergeTwoMetrics(MetricUtil.calculatePercentiles(wholePercentiles), MetricUtil.calculateAverages(wholeAverages))
        mergedMetrics = MetricUtil.mergeTwoMetrics(mergedMetrics, mergeMetrics(wholeMetrics))

        converter ! MetricMap(mergedMetrics, timeStamp)

        wholeMetrics.clear()
        wholeAverages.clear()
        wholePercentiles.clear()

        log.debug("[[[ Aggregator merge time 2 ]]] {}", System.currentTimeMillis() - startTime)
      }
    case HostMetricMap(metrics, averageMetrics, percentileMetrics, timeStamp) =>
      val startTime = System.currentTimeMillis()
      mergeWholeMetrics(metrics)
      wholeAverages = MetricUtil.mergeTwoListMetrics(averageMetrics, wholeAverages)
      wholePercentiles = MetricUtil.mergeTwoListMetrics(percentileMetrics, wholePercentiles)
      this.timeStamp = timeStamp

      log.debug("[[[ Aggregator merge time 1 ]]] {}", System.currentTimeMillis() - startTime)
  }

  private def mergeWholeMetrics(metrics:mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, Int]]) = {
    metrics.map { case (hostAddress, hostMetrics) =>
      if (wholeMetrics.contains(hostAddress)) wholeMetrics += (hostAddress -> MetricUtil.mergeTwoMetrics(hostMetrics, wholeMetrics(hostAddress)))
      else wholeMetrics += (hostAddress -> hostMetrics)
    }
  }

  private def mergeMetrics(metrics:mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, Int]]) = {
    var mergedMetrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap.empty[String, Int]

    metrics.values.foreach(hostMetrics => {
      mergedMetrics = MetricUtil.mergeTwoMetrics(hostMetrics, mergedMetrics)
    })

    mergedMetrics
  }
}
