package org.dsa.iot

import org.dsa.iot.util.EnvUtils

import scala.util.Random

/**
  * Constants and helper methods for benchmarks
  */
package object benchmark {

  val DefaultBrokerUrl = "http://localhost:8080/conn"

  val ResponderNamePrefix = "bmrsp"

  val RequesterNamePrefix = "bmreq"

  /**
    * Formats a dslink name.
    *
    * @param prefix
    * @param index
    * @return
    */
  def linkName(prefix: String, index: Int) = "%s%06d".format(prefix, index)

  /**
    * Formats a responder name.
    *
    * @param index
    * @return
    */
  def responderName(index: Int) = linkName(ResponderNamePrefix, index)

  /**
    * Formats a requester name.
    *
    * @param index
    * @return
    */
  def requesterName(index: Int) = linkName(RequesterNamePrefix, index)

  /**
    * Parses a string in the format 'x-y' and returns an inclusive range (x to y).
    *
    * @param str
    * @return
    */
  def parseRange(str: String) = {
    val bounds = str.split('-')
    require(bounds.size == 2)
    bounds(0).toInt to bounds(1).toInt
  }

  /**
    * @return a random broker url from the list supplied in system environment properties.
    */
  def randomBrokerUrl = {
    val urls = EnvUtils.getStringList("broker.url")
    if (!urls.isEmpty)
      urls(Random.nextInt(urls.size))
    else
      DefaultBrokerUrl
  }
}