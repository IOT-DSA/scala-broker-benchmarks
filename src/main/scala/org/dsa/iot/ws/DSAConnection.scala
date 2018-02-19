package org.dsa.iot.ws

import akka.actor.ActorRef
import akka.actor.PoisonPill

/**
 * Encapsulates a connection to a DSA broker.
 */
case class DSAConnection(wsActor: ActorRef) {
  def terminate() = wsActor ! PoisonPill
}