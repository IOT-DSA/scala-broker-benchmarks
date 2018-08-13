package org.dsa.iot.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.dsa.iot.actors.StatsCollector.{LogInboundMessage, LogOutboundMessage}
import org.dsa.iot.rpc._

/**
  * Base class for benchmark endpoint actors.
  */
abstract class WebSocketActor(linkName: String, linkType: LinkType, out: ActorRef, collector: ActorRef,
                              cfg: WebSocketActorConfig) extends Actor with ActorLogging {

  protected val localMsgId = new IntCounter(1)

  protected val scheduler = context.system.scheduler

  /**
    * Logs the actor stoppage.
    */
  override def postStop: Unit = log.info("[{}]: stopped", linkName)


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
    case m @ AllowedMessage(_, _) =>
      log.debug("[{}]: received \"allowed\" message from WebSocket, ignoring...", linkName)
      logInboundMessage(m)
  }

  /**
    * Sends an ACK back to the client.
    */
  private def sendAck(remoteMsgId: Int) = sendToSocket(PongMessage(remoteMsgId))

  /**
    * Sends a DSAMessage to a WebSocket connection.
    */
  protected def sendToSocket(msg: DSAMessage) = {
    log.debug("[{}]: sending {} to WebSocket", linkName, msg)
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
    * Sent by scheduler to initiate stats reporting.
    */
  case object StatsTick
}

/**
  * WebSocketActor configuration.
  */
trait WebSocketActorConfig

/**
  * WebSocketActorConfig implementation based on environment properties.
  */
abstract class EnvWebSocketActorConfig extends WebSocketActorConfig