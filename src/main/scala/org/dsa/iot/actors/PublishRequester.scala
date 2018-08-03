package org.dsa.iot.actors

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import org.dsa.iot.rpc.{ RequestMessage, ResponseMessage, SetRequest }

import akka.actor.{ ActorRef, Cancellable, Props }
import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicInteger
import org.joda.time.Interval

/**
 * A sample requester that repeatedly sends Set/Publish command to responders to change the value
 * of their nodes.
 */
class PublishRequester(linkName: String, paths: Iterable[String], timeout: FiniteDuration,
                       out: ActorRef, cfg: WebSocketActorConfig) extends WebSocketActor(linkName, false, true, out, cfg) {
  import PublishRequester._

  import context.dispatcher

  private val ridGen = new IntCounter(1)

  private var lastReportedAt: DateTime = _
  private val reqSent = new AtomicInteger(0)
  private val rspRcvd = new AtomicInteger(0)

  private var publishJob: Cancellable = null

  /**
   * Schedules a job to send Set/Publish requests.
   */
  override def preStart() = {
    super.preStart

    lastReportedAt = DateTime.now

    publishJob = context.system.scheduler.schedule(timeout, timeout, self, SendBatch)
    
//    publishJob = context.system.scheduler.schedule(timeout, timeout) {
//      val requests = paths map (path => SetRequest(ridGen.inc, path, Random.nextInt(1000)))
//      sendToSocket(RequestMessage(localMsgId.inc, None, requests.toList))
//      reqSent.addAndGet(requests.size)
//      log.debug("{}: sent a batch of {} SetRequests", linkName, paths.size)
//    }
  }

  /**
   * Terminates publish job.
   */
  override def postStop() = {
    publishJob.cancel

    super.postStop
  }

  /**
   * Handles incoming messages of `ResponseMessage` type.
   */
  override def receive = super.receive orElse {
    case msg: ResponseMessage =>
      log.debug("{}: received {}", linkName, msg)
      rspRcvd.addAndGet(msg.responses.size)
      
    case SendBatch =>
      val requests = paths map (path => SetRequest(ridGen.inc, path, Random.nextInt(1000)))
      sendToSocket(RequestMessage(localMsgId.inc, None, requests.toList))
      reqSent.addAndGet(requests.size)
      log.debug("{}: sent a batch of {} SetRequests", linkName, paths.size)

    case msg => log.warning("{}: received unknown message - {}", linkName, msg)
  }
  
  /**
   * Outputs requester statistics.
   */
  protected def reportStats(): Unit = {
    val now = DateTime.now
    val interval = new Interval(lastReportedAt, now)
    log.info("{}: interval={} requestsSent={} responsesRcvd={}", linkName, interval, reqSent.getAndSet(0), rspRcvd.getAndSet(0))
//    val stats = ReqStatsSample(linkName, interval, invokesSent.getAndSet(0), updatesRcvd.getAndSet(0))
//    log.debug("[{}]: collected {}", linkName, stats)
//    config.statsCollector foreach (_ ! stats)
    lastReportedAt = now
  }
}

/**
 * Factory for [[PublishRequester]] instances.
 */
object PublishRequester {
  /**
   * Creates a new PublishRequester props.
   */
  def props(linkName: String, paths: Iterable[String], timeout: FiniteDuration, out: ActorRef) =
    Props(new PublishRequester(linkName, paths, timeout, out, EnvBenchmarkResponderConfig))

  /**
   * Sent by the scheduler to initiate a request batch.
   */
  case object SendBatch
}