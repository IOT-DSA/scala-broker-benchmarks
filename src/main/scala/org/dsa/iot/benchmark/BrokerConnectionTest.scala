package org.dsa.iot.benchmark

import scala.concurrent.duration.DurationInt
import scala.util.Random

import org.slf4j.LoggerFactory

/**
 * Usage: BrokerConnectionTest [linkId]
 */
object BrokerConnectionTest extends App {
  import org.dsa.iot.scala.LinkMode._

  val log = LoggerFactory.getLogger(getClass)

  val id = args.headOption getOrElse Random.alphanumeric.take(5).mkString.capitalize
  val connector = createConnector(id, "/requester.json")
  val connection = connector.start(REQUESTER)
  implicit val requester = connection.requester

  pause(2 seconds)

  connector.stop
  sys.exit
}