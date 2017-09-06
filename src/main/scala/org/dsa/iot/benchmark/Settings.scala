package org.dsa.iot.benchmark

/**
 * Benchmark settings.
 */
object Settings {
  import scala.util.{ Properties => props }

  val BrokerUrl = props.envOrElse("dsa.brokerUrl", "http://localhost:8080/conn")

  val ExplorePath = props.envOrElse("dsa.explore.path", "/defs/profile")
  
  object Suite {
    val runConnectionTest = props.envOrElse("test.connection", "true").toBoolean
  }

  object Responder {
    val Id = props.envOrElse("responder.id", "SampleResponder")
    val NodeCount = props.envOrElse("responder.nodeCount", "100").toInt
    val AttributeCount = props.envOrElse("responder.attributeCount", "100").toInt
  }
}