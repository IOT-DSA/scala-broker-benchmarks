package org.dsa.iot.benchmark

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors.{BenchmarkResponder, LinkType, StatsCollector}
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

/**
  * Launches a set of BenchmarkResponders and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url          - DSA broker url, default "http://localhost:8080/conn"
  *   responder.instances - the number of responders to launch, default 1
  *   responder.nodes     - the number of nodes per responder, default 10
  *   responder.name      - the responder name prefix, default "benchmark-responder"
  *
  * Example: BenchmarkResponderApp -Dresponder.instances=5 -Dresponder.nodes=10
  */
object BenchmarkResponderApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = EnvUtils.getString("broker.url", "http://localhost:8080/conn")

  val instances = EnvUtils.getInt("responder.instances", 1)
  val nodeCount = EnvUtils.getInt("responder.nodes", 10)
  val namePrefix = EnvUtils.getString("responder.name", "benchmark-responder")

  log.info(
    "Launching {} responder instance(s), {} nodes each under name prefix '{}'",
    instances.toString, nodeCount.toString, namePrefix)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  val connections = (1 to instances) map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = namePrefix + index
    val propsFunc = (out: ActorRef) => BenchmarkResponder.props(name, out, collector)
    connector.connect(name, brokerUrl, LinkType.Responder, propsFunc)
  }

  sys.addShutdownHook {
    connections foreach (_ foreach (_.terminate))
    influx.close()
  }
}