package org.dsa.iot.benchmark

import scala.concurrent.duration.DurationInt

import org.slf4j.LoggerFactory

object RunTestSuite extends App {
  import Settings.Suite._

  val log = LoggerFactory.getLogger(getClass)
  val noargs = Array.empty[String]

  log.info("Starting DSA Broker Test Suite")

  if (runConnectionTest) {
    BrokerConnectionTest.main(noargs)
    pause(2 seconds)
  }

  if (runExploreTest) {
    BrokerExploreTest.main(noargs)
    pause(2 seconds)
  }

  SampleResponder.main(noargs)
}