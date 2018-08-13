package org.dsa.iot.benchmark

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import org.dsa.iot.actors._
import org.dsa.iot.handshake.LocalKeys
import org.dsa.iot.util.{EnvUtils, InfluxClient}
import org.dsa.iot.ws.WebSocketConnector
import org.slf4j.LoggerFactory

import scala.util.Random

/**
  * A load test that starts N responders and M requesters; requesters subscribe to random nodes and do NOT send
  * Invoke requests; responders keep updating their nodes randomly, so that the updates get delivered to the requesters.
  */
object AutoIncNoInvokeApp extends App {

  val log = LoggerFactory.getLogger(getClass)

  val brokerUrl = EnvUtils.getString("broker.url", "http://localhost:8080/conn")

  val reqInstances = EnvUtils.getInt("requester.instances", 1)
  val reqNamePrefix = EnvUtils.getString("requester.name", "benchmark-requester")
  val batchSize = EnvUtils.getInt("requester.batch", 10)
  val reqConfig = new BenchmarkRequesterConfig {
    val timeout = None
    val subscribe = true
  }

  val rspInstances = EnvUtils.getInt("responder.instances", 1)
  val rspNodeCount = EnvUtils.getInt("responder.nodes", 10)
  val rspNamePrefix = EnvUtils.getString("responder.name", "benchmark-responder")

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val influx = InfluxClient.getInstance

  val collector = system.actorOf(StatsCollector.props(influx, false))

  log.info(
    "Launching {} responder instance(s), {} nodes each under name prefix '{}'",
    rspInstances.toString, rspNodeCount.toString, rspNamePrefix)

  val rspConnections = (1 to rspInstances) map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = rspNamePrefix + index
    val propsFunc = (out: ActorRef) => BenchmarkResponder.props(name, out, collector)
    connector.connect(name, brokerUrl, LinkType.Responder, propsFunc)
  }

  // wait for the responders to ramp up
  Thread.sleep(5000)

  log.info(
    "Launching {} requester instance(s) with batches of {} under name prefix '{}'",
    reqInstances.toString, batchSize.toString, reqNamePrefix)

  val reqConnections = (1 to reqInstances) map { index =>
    val connector = new WebSocketConnector(LocalKeys.generate)
    val name = reqNamePrefix + index
    val paths = (1 to batchSize) map { _ =>
      val rspIndex = Random.nextInt(rspInstances) + 1
      val nodeIndex = Random.nextInt(rspNodeCount) + 1
      s"/downstream/$rspNamePrefix$rspIndex/data$nodeIndex"
    }
    val propsFunc = (out: ActorRef) => BenchmarkRequester.props(name, out, collector, paths.toSet, reqConfig)
    connector.connect(name, brokerUrl, LinkType.Requester, propsFunc)
  }

  sys.addShutdownHook {
    reqConnections foreach (_ foreach (_.terminate))
    rspConnections foreach (_ foreach (_.terminate))
    influx.close()
  }
}