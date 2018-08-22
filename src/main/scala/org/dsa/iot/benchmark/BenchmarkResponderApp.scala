package org.dsa.iot.benchmark

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors._
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

/**
  * Launches a set of BenchmarkResponders and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url                     - DSA broker url, default [[DefaultBrokerUrl]]
  *
  *   responder.count                - the number of responders to launch, default 1
  *   responder.nodes                - the number of nodes per responder, default 10
  *   responder.autoinc.interval     - the auto increment interval (optional, default is no auto-inc)
  */
object BenchmarkResponderApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = randomBrokerUrl

  val rspCount = EnvUtils.getInt("responder.count", 10)
  val nodeCount = EnvUtils.getInt("responder.nodes", 10)

  log.info("Launching {} responder(s) with {} nodes each", rspCount, nodeCount)

  val uuids = (1 to rspCount) map (_ => UUID.randomUUID.toString.replace('-', '_'))

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  val connections = uuids map { uuid =>
    val name = ResponderNamePrefix + uuid
    log.debug("Starting responder [{}]", name)
    Thread.sleep(500)
    val connector = new WebSocketConnector(LocalKeys.generate)
    val propsFunc = (out: ActorRef) => BenchmarkResponder.props(name, out, collector, EnvBenchmarkResponderConfig)
    connector.connect(name, brokerUrl, LinkType.Responder, propsFunc)
  }

  sys.addShutdownHook {
    connections foreach (_ foreach (_.terminate))
    influx.close()
  }
}

/**
  * BenchmarkResponderConfig implementation based on environment properties:
  *   responder.nodes                - the number of nodes per responder, default 10
  *   responder.autoinc.interval     - the auto increment interval (optional, default is no auto-inc)
  *   responder.autoinc.collate      - flag indicating whether to collate updates, default is false
  */
object EnvBenchmarkResponderConfig extends EnvWebSocketActorConfig with BenchmarkResponderConfig {

  val nodeCount: Int = EnvUtils.getInt("responder.nodes", 10)

  val autoIncInterval: Option[FiniteDuration] = EnvUtils.getMillisOption("responder.autoinc.interval")

  val collateAutoIncUpdates: Boolean = EnvUtils.getBoolean("responder.autoinc.collate", false)
}