package com.github.allenkim.qe.gatling.monitor.worker.message

import java.net.InetSocketAddress

import scala.collection.mutable

case class UdpMessage(udpString:String, remote:InetSocketAddress)
case class HostMetricMap(
                      metrics:mutable.LinkedHashMap[String, mutable.LinkedHashMap[String, Int]],
                      averageMetrics:mutable.LinkedHashMap[String, List[Int]],
                      percentileMetrics:mutable.LinkedHashMap[String, List[Int]],
                      timeStamp:Int
                    )
case class MetricMap(
                      metrics:mutable.LinkedHashMap[String, Int],
                      timeStamp:Int
                    )
