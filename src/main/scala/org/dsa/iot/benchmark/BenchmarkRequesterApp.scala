package org.dsa.iot.benchmark

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors._
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/**
  * Launches a set of BenchmarkRequesters and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url                - DSA broker url, default [[DefaultBrokerUrl]]
  *
  *   requester.range           - the requester index range in x-y format, default 1-1
  *   requester.batch           - the number of nodes to subscribe to actions triggered by requester
  *                               and/or the number of Invoke requests in a batch (per requester); default 10
  *   requester.timeout         - the interval between Invoke batches.
  *   requester.subscribe       - whether requester must subscribe to node updates initially.
  *
  *   responder.range           - the responder index range in x-y format, default 1-1
  *   responder.nodes           - the number of nodes per responder, default 10
  *
  * Note: Since the responders' target nodes are chosen randomly, there is a chance of duplicate
  * subscription/invocation paths in each requester's configuration, hence the total number of unique target
  * nodes per responder could be less than `requester.batch`.
  */
object BenchmarkRequesterApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = randomBrokerUrl

  val reqIndexRange = parseRange(EnvUtils.getString("requester.range", "1-1"))
  val batchSize = EnvUtils.getInt("requester.batch", 10)

  val rspIndexRange = parseRange(EnvUtils.getString("responder.range", "1-1"))
  val rspNodeCount = EnvUtils.getInt("responder.nodes", 10)

  log.info(
    "Launching {} requester(s) indexed from {} to {}, bound to {} nodes each",
    reqIndexRange.size: Integer, reqIndexRange.start: Integer, reqIndexRange.end: Integer, batchSize: Integer)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  val connections = reqIndexRange map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = requesterName(index)
    val paths = (1 to batchSize) map { _ =>
      val rspIndex = Random.nextInt(rspIndexRange.size) + rspIndexRange.start
      val rspName = responderName(rspIndex)
      val nodeIndex = Random.nextInt(rspNodeCount) + 1
      s"/downstream/$rspName/data$nodeIndex"
    }
    val propsFunc = (out: ActorRef) => BenchmarkRequester.props(name, out, collector, paths.toSet,
      EnvBenchmarkRequesterConfig)
    connector.connect(name, brokerUrl, LinkType.Requester, propsFunc)
  }

  sys.addShutdownHook {
    connections foreach (_ foreach (_.terminate))
    influx.close()
  }
}

/**
  * BenchmarkRequesterConfig implementation based on environment properties:
  *   requester.timeout   - the interval between invoke batches.
  *   requester.subscribe - whether requester must subscribe to node updates initially.
  */
object EnvBenchmarkRequesterConfig extends EnvWebSocketActorConfig with BenchmarkRequesterConfig {

  val timeout: Option[FiniteDuration] = EnvUtils.getMillisOption("requester.timeout")

  val subscribe: Boolean = EnvUtils.getBoolean("requester.subscribe", false)
}
