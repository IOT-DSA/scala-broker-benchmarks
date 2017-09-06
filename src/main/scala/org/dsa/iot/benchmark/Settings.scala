package org.dsa.iot.benchmark

import scala.concurrent.duration._

/**
 * Benchmark settings.
 */
object Settings {
  import scala.util.{ Properties => props }

  val BrokerUrl = props.envOrElse("dsa.brokerUrl", "http://localhost:8080/conn")

  val ExplorePath = props.envOrElse("dsa.explore.path", "/defs/profile")

  object Responder {
    val Id = props.envOrElse("responder.id", "SampleResponder")
    val NodeCount = props.envOrElse("responder.nodeCount", "100").toInt
    val AttributeCount = props.envOrElse("responder.attributeCount", "100").toInt
  }

  object Invoke {
    val BatchSize = props.envOrElse("invoke.batchSize", "10").toInt
    val Timeout = props.envOrElse("invoke.timeout", "1000").toInt milliseconds
  }

  object Publish {
    val AttributeBatchSize = props.envOrElse("publish.attributeBatchSize", "10").toInt
    val AttributeTimeout = props.envOrElse("publish.attributeTimeout", "1000").toInt milliseconds

    val ValueBatchSize = props.envOrElse("publish.valueBatchSize", "10").toInt
    val ValueTimeout = props.envOrElse("publish.valueTimeout", "1000").toInt milliseconds
  }
}