package org.dsa.iot.actors

import org.dsa.iot.rpc.{ DSAMessage, EmptyMessage, PingMessage }

import akka.actor.{ Actor, ActorLogging, ActorRef }

/**
 * Base class for benchmark endpoint actors.
 */
abstract class AbstractWebSocketActor(linkName: String, isRequester: Boolean, isResponder: Boolean,
                                      out: ActorRef) extends Actor with ActorLogging {

  protected val localMsgId = new IntCounter(1)

  override def preStart = log.info("[{}] started", linkName)

  override def postStop = log.info("[{}] stopped", linkName)

  def receive = {
    case EmptyMessage =>
      log.debug("{}: received empty message from WebSocket, ignoring...", linkName)
    case PingMessage(msg, ack) =>
      log.debug("{}: received ping from WebSocket with msg={}, acking...", linkName, msg)
      sendAck(msg)
  }

  /**
   * Sends an ACK back to the client.
   */
  private def sendAck(remoteMsgId: Int) = sendToSocket(PingMessage(localMsgId.inc, Some(remoteMsgId)))

  /**
   * Sends a DSAMessage to a WebSocket connection.
   */
  protected def sendToSocket(msg: DSAMessage) = {
    log.debug("{}: sending {} to WebSocket", linkName, msg)
    out ! msg
  }
}