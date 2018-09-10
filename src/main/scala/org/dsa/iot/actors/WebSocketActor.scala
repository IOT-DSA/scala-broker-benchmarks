package org.dsa.iot.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import org.dsa.iot.actors.StatsCollector.{LogInboundMessage, LogOutboundMessage}
import org.dsa.iot.actors.WebSocketActor._
import org.dsa.iot.rpc._

import scala.concurrent.duration._

/**
  * Base class for benchmark endpoint actors.
  */
abstract class WebSocketActor(linkName: String, linkType: LinkType, out: ActorRef, collector: ActorRef,
                              cfg: WebSocketActorConfig) extends Actor with ActorLogging {

  import context.dispatcher

  protected val localMsgId = new IntCounter(1)

  protected val scheduler = context.system.scheduler

  private var pingJob: Cancellable = _

  /**
    * Schedules regular ping requests to the broker.
    */
  override def preStart(): Unit = {
    pingJob = scheduler.schedule(PingInterval, PingInterval, self, PingTick)
  }

  /**
    * Logs the actor stoppage.
    */
  override def postStop: Unit = {
    pingJob.cancel()
    log.info("[{}]: stopped", linkName)
  }

  /**
    * Handles incoming messages.
    *
    * @return
    */
  def receive: Receive = {
    case m @ EmptyMessage         =>
      log.debug("[{}]: received empty message from WebSocket, ignoring...", linkName)
      logInboundMessage(m)
    case m @ PingMessage(msg, _)  =>
      log.debug("[{}]: received ping from WebSocket with msg={}, acking...", linkName, msg)
      sendAck(msg)
      logInboundMessage(m)
    case m @ PongMessage(ack)     =>
      log.debug("[{}]: received pong from WebSocket with ack={}, ignoring...", linkName, ack)
      logInboundMessage(m)
    case m @ AllowedMessage(_, _) =>
      log.debug("[{}]: received \"allowed\" message from WebSocket, ignoring...", linkName)
      logInboundMessage(m)
    case PingTick                 =>
      sendToSocket(PongMessage(localMsgId.inc))
  }

  /**
    * Sends an ACK back to the client.
    */
  protected def sendAck(remoteMsgId: Int) = sendToSocket(PongMessage(remoteMsgId))

  /**
    * Sends a DSAMessage to a WebSocket connection.
    */
  protected def sendToSocket(msg: DSAMessage) = {
    log.debug("[{}]: sending {} to WebSocket", linkName, formatMsg(msg))
    out ! msg
    logOutboundMessage(msg)
  }

  /**
    * Sends an inbound message to the stats collector.
    *
    * @param msg
    * @return
    */
  protected def logInboundMessage(msg: DSAMessage) = collector ! LogInboundMessage(linkName, linkType, msg)

  /**
    * Sends an outbound message to the stats collector.
    *
    * @param msg
    * @return
    */
  protected def logOutboundMessage(msg: DSAMessage) = collector ! LogOutboundMessage(linkName, linkType, msg)
}

/**
  * Constants and helper methods for AbstractWebSocketActor instances.
  */
object WebSocketActor {

  /**
    * Interval for regular Ping requests to the broker.
    */
  val PingInterval = 30 seconds

  /**
    * Sent by scheduler to initiate ping to the broker.
    */
  case object PingTick

}

/**
  * WebSocketActor configuration.
  */
trait WebSocketActorConfig

/**
  * WebSocketActorConfig implementation based on environment properties.
  */
abstract class EnvWebSocketActorConfig extends WebSocketActorConfig