package com.github.allenkim.qe.gatling.monitor.worker.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.routing._
import com.eakorea.qe.gatling.monitor.worker.constant.MessageName
import com.eakorea.qe.gatling.monitor.worker.message.UdpMessage
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class RouterCreator(system:ActorSystem, aggregator: ActorRef) extends Actor with ActorLogging {
  import system.dispatcher

  val config = ConfigFactory.load()
  val router = system.actorOf(RoundRobinGroup(List()).props(), "router")
  val routeeCount = config.getInt("worker.accumulatorCount")
  val accumulatorScheduleTime = config.getInt("worker.accumulatorScheduleTime")
  val props = Props(new Accumulator(aggregator))
  var childInstanceCount = 0

  override def preStart() = {
    super.preStart()
    (0 until routeeCount).map(count => createRoutee())
  }

  def createRoutee() = {
    childInstanceCount += 1
    val child = context.actorOf(props, "accumulator-" + childInstanceCount)
    system.scheduler.schedule(
      0 seconds,
      accumulatorScheduleTime seconds,
      child,
      MessageName.Tick
    )
    val selection = context.actorSelection(child.path)
    router ! AddRoutee(ActorSelectionRoutee(selection))
    context.watch(child)
  }

  override def receive: Receive = {
    case UdpMessage(data, remote) =>
      router ! UdpMessage(data, remote)

    case Terminated(child) =>
      router ! GetRoutees

    case routees: Routees => {
      import collection.JavaConverters._
      val active = routees.getRoutees.asScala.map {
        case x: ActorSelectionRoutee => x.selection.pathString
      }

      for (routee <- context.children) {
        val index = active.indexOf(routee.path.toStringWithoutAddress)
        if (index >= 0) {
          active.remove(index)
        } else {
          routee ! PoisonPill
        }
      }

      for (terminated <- active) {
        val name = terminated.substring(terminated.lastIndexOf("/") + 1)
        val child = context.actorOf(props, name)
        context.watch(child)
      }
    }
  }
}
