package org.dsa.iot.actors

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.Point
import org.dsa.iot.actors.StatsCollector.{LogInboundMessage, LogOutboundMessage}
import org.dsa.iot.rpc.DSAMethod.{Subscribe, Unsubscribe}
import org.dsa.iot.rpc._
import org.dsa.iot.util.InfluxClient

/**
  * Collects messages from benchmark actors and writes statistics to InfluxDB.
  *
  * @param influx
  * @param logAuxMessages
  */
class StatsCollector(influx: InfluxClient, logAuxMessages: Boolean) extends Actor with ActorLogging {

  implicit val precision = Precision.NANOSECONDS

  private var lastNanos: Long = 0L

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
  protected def message2points(linkName: String, linkType: LinkType, inbound: Boolean, ts: Instant)
                              (msg: DSAMessage) = {

    val current = ts.getEpochSecond * 1000000000L + ts.getNano
    lastNanos = if (current > lastNanos) current else lastNanos + 1

    val base = Point("message", lastNanos)
      .addTag("linkName", linkName)
      .addTag("linkType", linkType.toString)
      .addTag("msgType", msg.getClass.getSimpleName)
      .addTag("inbound", inbound.toString)

    msg match {
      case RequestMessage(_, _, requests)       =>
        base.addField("requests", requests.size) +: (requestsToPoints(linkName, linkType, inbound)(requests)).toList
      case ResponseMessage(_, _, responses) =>
        List(base
          .addField("responses", responses.size)
          .addField("updates", responses.map(_.updates.getOrElse(Nil).size).sum)
          .addField("errors", responses.map(_.error).filter(_.isDefined).size))
      case _ if logAuxMessages                  =>
        List(base.addField("dummy", -1))
      case _                                    =>
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
      case (Subscribe, reqs)   =>
        val subs = reqs.collect { case SubscribeRequest(_, paths) => paths.size }.sum
        base.addTag("method", Subscribe.toString).addField("reqs", reqs.size).addField("subs", subs)
      case (Unsubscribe, reqs) =>
        val unsubs = reqs.collect { case UnsubscribeRequest(_, sids) => sids.size }.sum
        base.addTag("method", Unsubscribe.toString).addField("reqs", reqs.size).addField("unsubs", unsubs)
      case (method, reqs)      =>
        base.addTag("method", method.toString).addField("reqs", reqs.size)
    }
  }
}

/**
  * Factory for [[StatsCollector]] instances.
  */
object StatsCollector {

  case class LogInboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: Instant = Instant.now)

  case class LogOutboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: Instant = Instant.now)

  /**
    * Creates a new StatsCollector props instance.
    *
    * @param influx
    * @param logAuxMessages
    * @return
    */
  def props(influx: InfluxClient, logAuxMessages: Boolean) = Props(new StatsCollector(influx, logAuxMessages))
}