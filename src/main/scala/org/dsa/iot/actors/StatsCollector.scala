package org.dsa.iot.actors

import akka.actor.{Actor, ActorLogging, Props}
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.Point
import org.dsa.iot.actors.StatsCollector.{LogInboundMessage, LogOutboundMessage}
import org.dsa.iot.rpc.{DSAMessage, DSARequest, RequestMessage, ResponseMessage}
import org.dsa.iot.util.InfluxClient
import org.joda.time.DateTime

/**
  * Collects messages from benchmark actors and writes statistics to InfluxDB.
  *
  * @param influx
  * @param logAuxMessages
  */
class StatsCollector(influx: InfluxClient, logAuxMessages: Boolean) extends Actor with ActorLogging {

  implicit val precision = Precision.MICROSECONDS

  def receive: Receive = {
    case LogInboundMessage(linkName, linkType, msg, ts) =>
      influx.bulkWrite(msg)(
        cnv = message2points(linkName, linkType, true, ts),
        precision = precision)

    case LogOutboundMessage(linkName, linkType, msg, ts) =>
      influx.bulkWrite(msg)(
        cnv = message2points(linkName, linkType, false, ts),
        precision = precision)
  }

  /**
    * Converts a DSAMessage instance into InfluxDB points.
    *
    * @param linkName
    * @param linkType
    * @param inbound
    * @param ts
    * @param msg
    * @return
    */
  protected def message2points(linkName: String, linkType: LinkType, inbound: Boolean, ts: DateTime)
                              (msg: DSAMessage) = {
    val base = Point("message", ts.getMillis * 1000)
      .addTag("linkName", linkName)
      .addTag("linkType", linkType.toString)
      .addTag("msgType", msg.getClass.getSimpleName)
      .addTag("inbound", inbound.toString)

    msg match {
      case RequestMessage(_, _, requests)   =>
        base.addField("requests", requests.size) +: (requestsToPoints(linkName, linkType, inbound)(requests)).toList
      case ResponseMessage(_, _, responses) =>
        List(base
          .addField("responses", responses.size)
          .addField("updates", responses.map(_.updates.getOrElse(Nil).size).sum)
          .addField("errors", responses.map(_.error).filter(_.isDefined).size))
      case _ if logAuxMessages              =>
        List(base.addField("dummy", -1))
      case _                                =>
        Nil
    }
  }

  /**
    * Converts a batch of requests into InfluxDB points.
    *
    * @param linkName
    * @param linkType
    * @param inbound
    * @param requests
    * @return
    */
  protected def requestsToPoints(linkName: String, linkType: LinkType, inbound: Boolean)
                                (requests: Iterable[DSARequest]) = {

    val base = Point("request")
      .addTag("linkName", linkName)
      .addTag("linkType", linkType.toString)
      .addTag("inbound", inbound.toString)

    requests.groupBy(_.method) map {
      case (method, reqs) => base.addTag("method", method.toString).addField("size", reqs.size)
    }
  }
}

/**
  * Factory for [[StatsCollector]] instances.
  */
object StatsCollector {

  case class LogInboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: DateTime = DateTime.now)

  case class LogOutboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: DateTime = DateTime.now)

  /**
    * Creates a new StatsCollector props instance.
    *
    * @param influx
    * @param logAuxMessages
    * @return
    */
  def props(influx: InfluxClient, logAuxMessages: Boolean) = Props(new StatsCollector(influx, logAuxMessages))
}