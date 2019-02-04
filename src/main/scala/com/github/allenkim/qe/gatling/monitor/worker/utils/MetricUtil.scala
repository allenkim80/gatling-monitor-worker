package com.github.allenkim.qe.gatling.monitor.worker.utils

import com.eakorea.qe.gatling.monitor.worker.constant.MetricPattern

import scala.collection.mutable

object MetricUtil {
  def calculateMetric(sourceMetrics:mutable.LinkedHashMap[String, Int], metricName:String, metricValue:Int) = {
    metricName match {
      case name if name.contains(MetricPattern.COUNT_METRIC_PATTERN) || name.contains(MetricPattern.USERS_METRIC_PATTERN) =>
        sourceMetrics += (name -> (sourceMetrics.getOrElse(name, 0) + metricValue))
      case name if name.contains(MetricPattern.MIN_METRIC_PATTERN) =>
        sourceMetrics += (name -> Math.min(sourceMetrics.getOrElse(name, Int.MaxValue), metricValue))
      case name if name.contains(MetricPattern.MAX_METRIC_PATTERN) =>
        sourceMetrics += (name -> Math.max(sourceMetrics.getOrElse(name, 0), metricValue))
      case _ => sourceMetrics += (metricName -> metricValue)
    }

    sourceMetrics
  }

  def mergeTwoMetrics(metricSource:mutable.LinkedHashMap[String, Int], metricDestination:mutable.LinkedHashMap[String, Int]) = {
    var mergedMetrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap.empty[String, Int]

    metricSource.foreach(metric => {
      val metricName = metric._1
      val metricValue = metric._2
      mergedMetrics = MetricUtil.calculateMetric(metricDestination, metricName, metricValue)
    })

    mergedMetrics
  }

  def mergeTwoListMetrics(metricSource:mutable.LinkedHashMap[String, List[Int]], metricDestination:mutable.LinkedHashMap[String, List[Int]]) = {
    metricSource.map { case (metricName, metricValue) =>
        metricDestination += (metricName -> (metricDestination.getOrElse(metricName, List()) ++ metricValue))
    }
    metricDestination
  }

  def calculateAverages(metrics:mutable.LinkedHashMap[String, List[Int]]) = {
    var averageMetrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap.empty[String, Int]
    metrics.map { case(metricName, metricValue) =>
      averageMetrics += (metricName -> (metricValue.sum / metricValue.length))
    }
    averageMetrics
  }

  def calculatePercentiles(metrics:mutable.LinkedHashMap[String, List[Int]]) = {
    var percentileMetrics:mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap.empty[String, Int]
    metrics.foreach(metric => {
      val metricName = metric._1
      val metricValues = metric._2.sorted
      val valueLength = if (metricValues.nonEmpty) metricValues.length - 1 else 0

      percentileMetrics.put(metricName + "50", metricValues((valueLength * 0.5).toInt))
      percentileMetrics.put(metricName + "75", metricValues((valueLength * 0.75).toInt))
      percentileMetrics.put(metricName + "95", metricValues((valueLength * 0.95).toInt))
      percentileMetrics.put(metricName + "99", metricValues((valueLength * 0.99).toInt))
    })

    percentileMetrics
  }
}
