package org.dsa.iot.benchmark

/**
 * Benchmark settings.
 */
object Settings {
  val BrokerUrl = scala.util.Properties.envOrElse("dsa.brokerUrl", "http://localhost:8080/conn")
}