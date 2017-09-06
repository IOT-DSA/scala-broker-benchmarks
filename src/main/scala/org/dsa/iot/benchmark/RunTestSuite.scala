package org.dsa.iot.benchmark

import org.slf4j.LoggerFactory

object RunTestSuite extends App {
  val log = LoggerFactory.getLogger(getClass)
  
  log.info("Starting DSA Broker Test Suite")
  
  SampleResponder.main(Array.empty[String])
}