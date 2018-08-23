package org.dsa.iot.benchmark

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors.{BenchmarkResponder, BenchmarkResponderConfig, LinkType, StatsCollector}
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.InfluxClient
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

/**
  * A simple broker connection test.
  *
  * It accepts the following environment properties:
  *   broker.url          - DSA broker url, default [[DefaultBrokerUrl]]
  */
object ConnectionTest extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = randomBrokerUrl

  log.info("Launching a connection test for broker at {}", brokerUrl)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  val connector = new WebSocketConnector(LocalKeys.generate)
  val dslinkName = "benchmark-test"
  val cfg = new BenchmarkResponderConfig {
    val nodeCount = 1
    val autoIncInterval = None
    val collateAutoIncUpdates: Boolean = false
  }

  val propsFunc = (out: ActorRef) => BenchmarkResponder.props(dslinkName, out, collector, cfg)
  val connection = connector.connect(dslinkName, brokerUrl, LinkType.Responder, propsFunc)

  connection foreach { conn =>
    log.info("Connection to {} established successfully", brokerUrl)
    conn.terminate()
    Thread.sleep(1000)
    log.info("Connection to {} shut down", brokerUrl)
    influx.close()
    sys.exit
  }
}
