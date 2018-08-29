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
import scala.util.Random

/**
  * Launches a set of BenchmarkRequesters and establishes connections to a DSA broker.
  *
  * It accepts the following environment properties:
  *   broker.url                - DSA broker url, default [[DefaultBrokerUrl]]
  *
  *   requester.count           - the number of requesters to launch, default 1
  *   requester.batch           - the number of nodes to subscribe to actions triggered by requester
  *                               and/or the number of Invoke requests in a batch (per requester); default 10
  *   requester.timeout         - the interval between Invoke batches
  *   requester.subscribe       - whether requester must subscribe to node updates initially
  *   rampup.delay              - delay between launching dslinks, default is 100 ms
  *   stats.interval            - the interval for batching stats before sending them to InfluxDB, default 1s
  */
object BenchmarkRequesterApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = randomBrokerUrl

  val reqCount = EnvUtils.getInt("requester.count", 1)
  val batchSize = EnvUtils.getInt("requester.batch", 10)
  val rampupDelay = EnvUtils.getMillis("rampup.delay", 100 milliseconds)

  val statsInterval = EnvUtils.getMillis("stats.interval", 1 second)

  log.info(s"Launching $reqCount requester(s), bound to $batchSize nodes each to connect to $brokerUrl")

  val uuids = (1 to reqCount) map (_ => UUID.randomUUID.toString.replace('-', '_'))

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false, statsInterval))

  val responderRanges = getAllResponderRanges

  val connections = uuids map { uuid =>
    Thread.sleep(rampupDelay.toMillis)
    val name = RequesterNamePrefix + uuid
    log.debug("Starting requester [{}]", name)
    val connector = new WebSocketConnector(LocalKeys.generate)
    responderRanges map getRandomResponderPaths(batchSize) flatMap { paths =>
      val propsFunc = (out: ActorRef) => BenchmarkRequester.props(name, out, collector, paths, EnvBenchmarkRequesterConfig)
      connector.connect(name, brokerUrl, LinkType.Requester, propsFunc)
    }
  }

  sys.addShutdownHook {
    connections foreach (_ foreach (_.terminate))
    influx.close()
  }

  /**
    * Retrieves all responder names and their node ranges from InfluxDB.
    *
    * @return
    */
  private def getAllResponderRanges = {
    val fqr = influx.query("select * from rsp_config")

    fqr map { qr =>
      val allRecords = qr.series flatMap (_.records)
      allRecords map { record =>
        val linkName = record("linkName").toString
        val nodeCount = record("nodeCount").asInstanceOf[Number].intValue
        val range = 1 to nodeCount
        linkName -> range
      }
    }
  }

  /**
    * Randomly selects a subset of responder paths.
    *
    * @param count
    * @param nodesAndRanges
    * @return
    */
  private def getRandomResponderPaths(count: Int)(nodesAndRanges: List[(String, Range)]) = {
    val tuples = nodesAndRanges flatMap {
      case (name, range) => range.map(index => name -> index)
    }
    Random.shuffle(tuples).take(count) map {
      case (name, index) => s"/downstream/$name/data$index"
    }
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
