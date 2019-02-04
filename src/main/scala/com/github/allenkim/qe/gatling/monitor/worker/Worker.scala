package com.github.allenkim.qe.gatling.monitor.worker

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import com.eakorea.qe.gatling.monitor.worker.actor._
import com.eakorea.qe.gatling.monitor.worker.constant.MessageName
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._


object Worker extends App {
  import system.dispatcher

  val config = ConfigFactory.load()
  val dbHost = config.getString("worker.dbHost")
  val dbPort = config.getInt("worker.dbPort")
  val accumulatorScheduleTime = config.getInt("worker.accumulatorScheduleTime")
  val aggregatorScheduleTime = config.getInt("worker.aggregatorScheduleTime")
  val system = ActorSystem("gatling-monitor-worker")

  val tcpClient = system.actorOf(TcpClient.props(new InetSocketAddress(dbHost, dbPort)), "tcpClient")

  val aggregator = system.actorOf(Aggregator.props(tcpClient), "aggregator")
  system.scheduler.schedule(
    accumulatorScheduleTime seconds,
    aggregatorScheduleTime seconds,
    aggregator,
    MessageName.Tick
  )

  val router = system.actorOf(Props(new RouterCreator(system, aggregator)))

  val udpListener = system.actorOf(UdpListener.props(router), "updListener")
}
