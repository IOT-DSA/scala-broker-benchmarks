package org.dsa.iot.benchmark

import org.slf4j.LoggerFactory

object RunTestSuite extends App {
  import Settings.Suite._

  val log = LoggerFactory.getLogger(getClass)
  val noargs = Array.empty[String]

  log.info("Starting DSA Broker Test Suite")

  if (runConnectionTest) {
    BrokerConnectionTest.main(noargs)
    Thread.sleep(2000)
  }

  SampleResponder.main(noargs)
}