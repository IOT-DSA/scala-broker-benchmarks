package org.dsa.iot.actors

/**
  * DSLink type.
  */
sealed trait LinkType {
  def isRequester: Boolean
  def isResponder: Boolean
}

/**
  * Pre-defined link types.
  */
object LinkType {

  case object Requester extends LinkType {
    val isRequester: Boolean = true
    val isResponder: Boolean = false
  }

  case object Responder extends LinkType {
    val isRequester: Boolean = false
    val isResponder: Boolean = true
  }

  case object Dual extends LinkType {
    val isRequester: Boolean = true
    val isResponder: Boolean = true
  }
}