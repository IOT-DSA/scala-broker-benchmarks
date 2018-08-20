package org.dsa.iot.actors

import akka.actor.{ActorRef, Cancellable, Props}
import org.dsa.iot.actors.StatsCollector.LogRequesterConfig
import org.dsa.iot.rpc._

import scala.concurrent.duration._

/**
  * A requester that (optionally) subscribes for updates from a number of nodes and then
  * periodically calls "incCounter" action on each node to trigger updates.
  *
  * @param linkName
  * @param out
  * @param collector
  * @param paths
  * @param cfg
  */
class BenchmarkRequester(linkName: String, out: ActorRef, collector: ActorRef, paths: Iterable[String],
                         cfg: BenchmarkRequesterConfig)
  extends WebSocketActor(linkName, LinkType.Requester, out, collector, cfg) {

  import BenchmarkRequester._
  import context.dispatcher

  private val ridGen = new IntCounter(1)
  private val startSid = 101
  private val invPaths = paths map (_ + "/incCounter")

  private var invokeJob: Option[Cancellable] = None

  /**
    * Subscribes for updates and schedules an invocation job.
    */
  override def preStart: Unit = {
    log.debug("[{}]: starting requester with paths {}", linkName, paths)

    collector ! LogRequesterConfig(linkName, paths, cfg)

    // subscribe for node updates
    if (cfg.subscribe) {
      val subPaths = paths.zipWithIndex map {
        case (path, index) => SubscriptionPath(path, startSid + index)
      }
      val subReq = SubscribeRequest(ridGen.inc, subPaths.toList)
      sendToSocket(RequestMessage(localMsgId.inc, None, List(subReq)))
      log.debug("[{}]: subscribed to {} paths", linkName, paths.size)
    }

    // schedule action invocation
    invokeJob = cfg.timeout map (to => scheduler.schedule(to, to, self, SendBatch))

    log.info("[{}]: started with {} paths{}, {}", linkName, paths.size,
      if (cfg.subscribe) " with subscription" else " without subscription",
      cfg.timeout.map("invoke timeout of " + _.toString).getOrElse("no invokes"))
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
      log.debug("[{}]: received {}", linkName, formatMsg(msg))
      logInboundMessage(msg)

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
    * @param collector
    * @param paths
    * @param cfg
    * @return
    */
  def props(linkName: String, out: ActorRef, collector: ActorRef, paths: Iterable[String], cfg: BenchmarkRequesterConfig) =
    Props(new BenchmarkRequester(linkName, out, collector, paths, cfg))
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