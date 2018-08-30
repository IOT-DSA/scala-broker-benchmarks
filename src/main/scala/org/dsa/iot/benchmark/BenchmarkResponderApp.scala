package org.dsa.iot.benchmark

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors._
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
  * Launches a set of BenchmarkResponders and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url                     - DSA broker url, default [[DefaultBrokerUrl]]
  *
  *   responder.count                - the number of responders to launch, default 1
  *   responder.nodes                - the number of nodes per responder, default 10
  *   responder.autoinc.interval     - the auto increment interval (optional, default is no auto-inc)
  *   rampup.delay                   - delay between launching dslinks, default is 100 ms
  *   stats.interval                 - the interval for batching stats before sending them to InfluxDB, default 1s
  */
object BenchmarkResponderApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = randomBrokerUrl

  val rspCount = EnvUtils.getInt("responder.count", 1)
  val nodeCount = EnvUtils.getInt("responder.nodes", 10)
  val rampupDelay = EnvUtils.getMillis("rampup.delay", 100 milliseconds)

  val statsInterval = EnvUtils.getMillis("stats.interval", 1 second)

  log.info(s"Launching $rspCount responder(s) with $nodeCount nodes each to connect to $brokerUrl")

  val uuids = (1 to rspCount) map (_ => UUID.randomUUID.toString.replace('-', '_'))

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false, statsInterval))

  val connections = uuids map { uuid =>
    Thread.sleep(rampupDelay.toMillis)
    val name = ResponderNamePrefix + uuid
    log.debug("Starting responder [{}]", name)
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