package org.dsa.iot.benchmark

import org.dsa.iot.actors.BenchmarkResponder
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.ActorMaterializer

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
  import scala.util.{ Properties => props }

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = props.envOrElse("broker.url", "http://localhost:8080/conn")

  val instances = props.envOrElse("responder.instances", "5").toInt
  val nodeCount = props.envOrElse("responder.nodes", "10").toInt
  val namePrefix = props.envOrElse("responder.name", "benchmark-responder")

  log.info(
    "Launching {} responder instances, {} nodes each under name prefix '{}'",
    instances.toString, nodeCount.toString, namePrefix)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val keys = LocalKeys.getFromClasspath("/keys")
  val connector = new WebSocketConnector(keys)

  val connections = (1 to instances) map { index =>
    val name = namePrefix + index
    val propsFunc = (out: ActorRef) => BenchmarkResponder.props(name, nodeCount, out)
    connector.connect(name, brokerUrl, false, true, propsFunc)
  }

  sys.addShutdownHook(connections foreach {
    _ foreach (_.terminate)
  })
}