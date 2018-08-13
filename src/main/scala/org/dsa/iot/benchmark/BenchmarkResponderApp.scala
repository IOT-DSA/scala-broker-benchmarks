package org.dsa.iot.benchmark

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
  *   responder.range                - the responder index range in x-y format, default 1-1
  *   responder.nodes                - the number of nodes per responder, default 10
  *   responder.autoinc.interval     - the auto increment interval (optional, default is no auto-inc)
  */
object BenchmarkResponderApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = EnvUtils.getString("broker.url", DefaultBrokerUrl)

  val indexRange = parseRange(EnvUtils.getString("responder.range", "1-1"))
  val nodeCount = EnvUtils.getInt("responder.nodes", 10)

  log.info(
    "Launching {} responder(s) indexed from {} to {} with {} nodes each",
    indexRange.size: Integer, indexRange.start: Integer, indexRange.end: Integer, nodeCount: Integer)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  val connections = indexRange map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = responderName(index)
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
  */
object EnvBenchmarkResponderConfig extends EnvWebSocketActorConfig with BenchmarkResponderConfig {

  val nodeCount: Int = EnvUtils.getInt("responder.nodes", 10)

  val autoIncInterval: Option[FiniteDuration] = EnvUtils.getMillisOption("responder.autoinc.interval")
}