package org.dsa.iot.util

import scala.concurrent.duration._

/**
  * Helper methods for accessing environment properties.
  */
object EnvUtils {

  import scala.util.{Properties => props}

  /* optional */

  def getStringOption(name: String) = props.envOrNone(name)

  def getIntOption(name: String) = props.envOrNone(name) map (_.toInt)

  def getLongOption(name: String) = props.envOrNone(name) map (_.toLong)

  def getBooleanOption(name: String) = props.envOrNone(name) map (_.toBoolean)

  def getMillisOption(name: String) = getLongOption(name) map (_.millis)

  /* with default */

  def getString(name: String, alt: String) = getStringOption(name) getOrElse alt

  def getInt(name: String, alt: Int) = getIntOption(name) getOrElse alt

  def getLong(name: String, alt: Long) = getLongOption(name) getOrElse alt

  def getBoolean(name: String, alt: Boolean) = getBooleanOption(name) getOrElse alt

  def getMillis(name: String, alt: FiniteDuration) = getMillisOption(name) getOrElse alt

  /* lists */

  def getStringList(name: String, separator: String = ",") = getList(name, identity[String], separator)

  def getIntList(name: String, separator: String = ",") = getList(name, _.toInt, separator)

  def getLongList(name: String, separator: String = ",") = getList(name, _.toLong, separator)

  def getBooleanList(name: String, separator: String = ",") = getList(name, _.toBoolean, separator)

  private def getList[T](name: String, parser: String => T,
                         separator: String = ",") = getStringOption(name) map {
    _.split(separator).map(parser).toList
  } getOrElse Nil
}
