package org.dsa.iot.benchmark

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors.{BenchmarkRequester, LinkType}
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import scala.util.Random

/**
  * Launches a set of BenchmarkRequesters and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url          - DSA broker url, default "http://localhost:8080/conn"
  *   requester.instances - the number of requesters to launch, default 1
  *   requester.batch     - the number of node actions triggered by requester, default 10
  *   requester.name      - the requester name prefix, default "benchmark-responder"
  *   requester.timeout   - the interval between job invocations.
  *   requester.subscribe - whether requester must subscribe to node updates initially.
  *   responder.instances - the number of responders to launch, default 1
  *   responder.nodes     - the number of nodes per responder, default 10
  *   responder.name      - the responder name prefix, default "benchmark-responder"
  *
  * Example: BenchmarkResponderApp -Dresponder.instances=5 -Dresponder.nodes=10
  *
  * Note: Since the responders' target nodes are chosen randomly, there is a chance of duplicate
  * subscription/invocation paths in each requester's configuration, hence the total number of unique target
  * nodes per responder could be less than `requester.batch`.
  */
object BenchmarkRequesterApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = EnvUtils.getString("broker.url", "http://localhost:8080/conn")

  val reqInstances = EnvUtils.getInt("requester.instances", 1)
  val reqNamePrefix = EnvUtils.getString("requester.name", "benchmark-requester")
  val batchSize = EnvUtils.getInt("requester.batch", 10)

  val rspInstances = EnvUtils.getInt("responder.instances", 1)
  val rspNodeCount = EnvUtils.getInt("responder.nodes", 10)
  val rspNamePrefix = EnvUtils.getString("responder.name", "benchmark-responder")

  log.info(
    "Launching {} requester instance(s) with batches of {} under name prefix '{}'",
    reqInstances.toString, batchSize.toString, reqNamePrefix)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val connections = (1 to reqInstances) map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = reqNamePrefix + index
    val paths = (1 to batchSize) map { _ =>
      val rspIndex = Random.nextInt(rspInstances) + 1
      val nodeIndex = Random.nextInt(rspNodeCount) + 1
      s"/downstream/$rspNamePrefix$rspIndex/data$nodeIndex"
    }
    val propsFunc = (out: ActorRef) => BenchmarkRequester.props(name, out, influx, paths.toSet)
    connector.connect(name, brokerUrl, LinkType.Requester, propsFunc)
  }

  sys.addShutdownHook {
    connections foreach (_ foreach (_.terminate))
    influx.close()
  }
}
