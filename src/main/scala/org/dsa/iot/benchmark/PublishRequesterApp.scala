package org.dsa.iot.benchmark

import scala.concurrent.duration.DurationInt
import scala.util.Random

import org.dsa.iot.actors.PublishRequester
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import akka.actor.{ ActorRef, ActorSystem }
import akka.stream.ActorMaterializer

/**
 * Launches a set of PublishRequesters and establishes connections to a DSA broker.
 *
 * It accepts the following environment properties:
 *   broker.url          - DSA broker url, default "http://localhost:8080/conn"
 *
 *   requester.instances - the number of requesters to launch, default 1
 *   requester.name      - the requester name prefix, default "publish-requester"
 *   requester.batch     - the number of requests in a batch sent by requester, default 10
 *   requester.timeout   - the timeout (in milliseconds) between requester batches, default 1000
 *
 *   responder.instances - the number of responders, default 1
 *   responder.nodes     - the number of nodes per responder, default 10
 *   responder.name      - the responder name prefix, default "benchmark-responder"
 *
 * Example: PublishRequesterApp -Drequester.instances=20 -Drequester.timeout=500
 */
object PublishRequesterApp extends App {
  import scala.util.{ Properties => props }

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = props.envOrElse("broker.url", "http://localhost:8080/conn")

  val reqInstances = props.envOrElse("requester.instances", "1").toInt
  val reqNamePrefix = props.envOrElse("requester.name", "publish-requester")
  val batchSize = props.envOrElse("requester.batch", "10").toInt
  val timeout = props.envOrElse("requester.timeout", "1000").toInt.millis

  val rspInstances = props.envOrElse("responder.instances", "1").toInt
  val rspNodeCount = props.envOrElse("responder.nodes", "10").toInt
  val rspNamePrefix = props.envOrElse("responder.name", "benchmark-responder")

  log.info(
    "Launching {} requester instance(s) with batches of {} under name prefix '{}'",
    reqInstances.toString, batchSize.toString, reqNamePrefix)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val keys = LocalKeys.generate
  val connector = new WebSocketConnector(keys)

  val connections = (1 to reqInstances) map { index =>
    val name = reqNamePrefix + index
    val paths = (1 to batchSize) map { _ =>
      val rspIndex = Random.nextInt(rspInstances) + 1
      val nodeIndex = Random.nextInt(rspNodeCount) + 1
      s"/downstream/$rspNamePrefix$rspIndex/data$nodeIndex"
    }
    val propsFunc = (out: ActorRef) => PublishRequester.props(name, paths, timeout, out)
    connector.connect(name, brokerUrl, true, false, propsFunc)
  }

  sys.addShutdownHook(connections foreach {
    _ foreach (_.terminate)
  })
}