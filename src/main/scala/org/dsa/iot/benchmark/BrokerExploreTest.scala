package org.dsa.iot.benchmark

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.duration.DurationInt
import scala.util.Random

import org.dsa.iot.scala.DSAHelper
import org.slf4j.LoggerFactory

/**
 * Usage: BrokerExploreTest [linkId]
 */
object BrokerExploreTest extends App {
  import org.dsa.iot.scala.LinkMode._

  val log = LoggerFactory.getLogger(getClass)
  
  log.info("Starting Broker Explorer Test")

  val id = args.headOption getOrElse Random.alphanumeric.take(5).mkString.capitalize
  val connector = createConnector(id, "/requester.json")
  val connection = connector.start(REQUESTER)
  implicit val requester = connection.requester

  DSAHelper.list(Settings.ExplorePath).subscribe { rsp =>
    println(nodeInfo(rsp.getNode))
    println("Children:")
    rsp.getUpdates.asScala collect {
      case (node, _) => println(nodeInfo(node))
    }
    println("----------------")
  }

  pause(5 seconds)

  connector.stop
}