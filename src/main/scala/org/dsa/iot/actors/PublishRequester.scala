package org.dsa.iot.actors

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import org.dsa.iot.rpc.{ RequestMessage, ResponseMessage, SetRequest }

import akka.actor.{ ActorRef, Cancellable, Props }

/**
 * A sample requester that repeatedly sends Set/Publish command to responders to change the value
 * of their nodes.
 */
class PublishRequester(linkName: String, paths: Iterable[String], timeout: FiniteDuration,
                       out: ActorRef) extends AbstractWebSocketActor(linkName, false, true, out) {

  import context.dispatcher

  private val ridGen = new IntCounter(1)

  private var publishJob: Cancellable = null

  /**
   * Schedules a job to send Set/Publish requests.
   */
  override def preStart() = {
    super.preStart

    publishJob = context.system.scheduler.schedule(timeout, timeout) {
      val requests = paths map (path => SetRequest(ridGen.inc, path, Random.nextInt(1000)))
      sendToSocket(RequestMessage(localMsgId.inc, None, requests.toList))
      log.debug("{}: sent a batch of {} SetRequests", linkName, paths.size)
    }
  }

  /**
   * Terminates publish job.
   */
  override def postStop() = {
    publishJob.cancel

    super.postStop
  }

  /**
   * Handles incoming messages of [[ResponseMessage]] type.
   */
  override def receive = super.receive orElse {
    case msg: ResponseMessage =>
      log.debug("{}: received {}", linkName, msg)

    case msg => log.warning("{}: received unknown message - {}", linkName, msg)
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
    Props(new PublishRequester(linkName, paths, timeout, out))
}