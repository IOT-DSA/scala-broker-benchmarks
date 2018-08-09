package org.dsa.iot.actors

import akka.actor.{ActorRef, Cancellable, Props}
import org.dsa.iot.rpc._
import org.dsa.iot.util.{EnvUtils, InfluxClient}

import scala.concurrent.duration._

/**
  * A requester that (optionally) subscribes for updates from a number of nodes and then
  * periodically calls "incCounter" action on each node to trigger updates.
  *
  * @param linkName
  * @param out
  * @param influx
  * @param paths
  * @param cfg
  */
class BenchmarkRequester(linkName: String, out: ActorRef, influx: InfluxClient, paths: Iterable[String],
                         cfg: BenchmarkRequesterConfig)
  extends WebSocketActor(linkName, LinkType.Requester, out, influx, cfg) {

  import BenchmarkRequester._
  import context.dispatcher

  private val ridGen = new IntCounter(1)
  private val startSid = 101
  private val invPaths = paths map (_ + "/incCounter")

  private var invokeJob: Option[Cancellable] = None

  /**
    * Schedules an invocation job.
    */
  override def preStart: Unit = {
    log.info("[{}]: starting with {} paths, invoke timeout {}", linkName, paths.size, cfg.timeout)

    // subscribe for node updates
    if (cfg.subscribe) {
      val subPaths = paths.zipWithIndex map {
        case (path, index) => SubscriptionPath(path, startSid + index)
      }
      val subReq = SubscribeRequest(ridGen.inc, subPaths.toList)
      sendToSocket(RequestMessage(localMsgId.inc, None, List(subReq)))
      log.info("[{}]: subscribed to {} paths", linkName, paths.size)
    }

    // schedule action invocation
    invokeJob = cfg.timeout map (to => scheduler.schedule(to, to, self, SendBatch))

    super.preStart
  }

  /**
    * Stops the invocation job.
    */
  override def postStop: Unit = {
    invokeJob foreach { job =>
      log.info("[{}]: canceling invocations", linkName)
      job.cancel
    }

    if (cfg.subscribe) {
      val sids = (startSid until startSid + paths.size)
      val unsReq = UnsubscribeRequest(ridGen.inc, sids.toList)
      sendToSocket(RequestMessage(localMsgId.inc, None, List(unsReq)))
      log.info("[{}]: unsubscribed from {} paths", linkName, paths.size)
    }

    super.postStop
  }

  /**
    * Handles incoming messages.
    *
    * @return
    */
  override def receive: Receive = super.receive orElse {

    case msg: ResponseMessage =>
      log.debug("[{}]: received {}", linkName, msg)
      reportInboundMessage(msg)

    case SendBatch =>
      val requests = invPaths map (InvokeRequest(ridGen.inc, _))
      sendToSocket(RequestMessage(localMsgId.inc, None, requests.toList))
      log.debug("[{}]: sent a batch of {} InvokeRequests", linkName, invPaths.size)

    case msg => log.warning("[{}]: received unknown message - {}", linkName, msg)
  }
}

/**
  * Factory for [[BenchmarkRequester]] instances.
  */
object BenchmarkRequester {

  /**
    * Sent by scheduler to initiate invoke job.
    */
  case object SendBatch

  /**
    * Creates a new BenchmarkRequester props.
    *
    * @param linkName
    * @param out
    * @param influx
    * @param paths
    * @param cfg
    * @return
    */
  def props(linkName: String, out: ActorRef, influx: InfluxClient, paths: Iterable[String],
            cfg: BenchmarkRequesterConfig = EnvBenchmarkRequesterConfig) =
    Props(new BenchmarkRequester(linkName, out, influx, paths, cfg))
}

/**
  * BenchmarkRequester configuration.
  */
trait BenchmarkRequesterConfig extends WebSocketActorConfig {

  /**
    * @return interval between invoke jobs triggers (if None, that means no invoke requests will be sent).
    */
  def timeout: Option[FiniteDuration]

  /**
    * @return whether requester must subscribe to node updates initially.
    */
  def subscribe: Boolean
}

/**
  * BenchmarkRequesterConfig implementation based on environment properties.
  */
object EnvBenchmarkRequesterConfig extends EnvWebSocketActorConfig with BenchmarkRequesterConfig {

  val timeout: Option[FiniteDuration] = EnvUtils.getMillisOption("requester.timeout")

  val subscribe: Boolean = EnvUtils.getBoolean("requester.subscribe", false)
}
