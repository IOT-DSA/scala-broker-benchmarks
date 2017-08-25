package org.dsa.iot.benchmark

import com.typesafe.config.ConfigFactory

/**
 * Benchmark settings.
 */
object Settings {
  private lazy val root = ConfigFactory.load
  
  val BrokerUrl = root.getString("dsa.brokerUrl")
}