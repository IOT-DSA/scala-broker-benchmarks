package org.dsa.iot.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import org.dsa.iot.rpc.{DSAMessage, EmptyMessage, PingMessage, PongMessage}
import org.dsa.iot.util.EnvUtils

import scala.concurrent.duration._

/**
  * Base class for benchmark endpoint actors.
  */
abstract class WebSocketActor(linkName: String, isRequester: Boolean, isResponder: Boolean,
                              out: ActorRef, cfg: WebSocketActorConfig) extends Actor with ActorLogging {

  import WebSocketActor._
  import context.dispatcher

  protected val localMsgId = new IntCounter(1)

  private var statsJob: Cancellable = _

  /**
    * Schedules a stats job.
    */
  override def preStart: Unit = {
    log.debug("{}: scheduling stats reporting at {}", linkName, cfg.statsInterval)
    statsJob = context.system.scheduler.schedule(cfg.statsInterval, cfg.statsInterval, self, StatsTick)

    log.info("{}: started", linkName)
  }

  /**
    * Stops the stats job.
    */
  override def postStop: Unit = {
    log.debug("{}: canceling stats scheduler", linkName)
    statsJob.cancel

    log.info("{}: stopped", linkName)
  }

  /**
    * Handles incoming messages.
    *
    * @return
    */
  def receive: Receive = {
    case EmptyMessage        =>
      log.debug("{}: received empty message from WebSocket, ignoring...", linkName)
    case PingMessage(msg, _) =>
      log.debug("{}: received ping from WebSocket with msg={}, acking...", linkName, msg)
      sendAck(msg)
    case StatsTick           => reportStats
  }

  /**
    * Overridden by subclasses to report stats data.
    */
  protected def reportStats(): Unit

  /**
    * Sends an ACK back to the client.
    */
  private def sendAck(remoteMsgId: Int) = sendToSocket(PongMessage(remoteMsgId))

  /**
    * Sends a DSAMessage to a WebSocket connection.
    */
  protected def sendToSocket(msg: DSAMessage) = {
    log.debug("{}: sending {} to WebSocket", linkName, msg)
    out ! msg
  }
}

/**
  * Constants and helper methods for AbstractWebSocketActor instances.
  */
object WebSocketActor {

  /**
    * Sent by scheduler to initiate stats reporting.
    */
  case object StatsTick

}

/**
  * WebSocketActor configuration.
  */
trait WebSocketActorConfig {
  /**
    * @return statistics reporting interval.
    */
  def statsInterval: FiniteDuration
}

/**
  * WebSocketActorConfig implementation based on environment properties.
  */
abstract class EnvWebSocketActorConfig extends WebSocketActorConfig {
  val statsInterval = EnvUtils.getMillis("stats.interval", 10 seconds)
}