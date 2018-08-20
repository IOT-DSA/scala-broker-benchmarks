package org.dsa.iot.actors

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.Point
import org.dsa.iot.actors.StatsCollector._
import org.dsa.iot.rpc.DSAMethod.{Subscribe, Unsubscribe}
import org.dsa.iot.rpc._
import org.dsa.iot.util.InfluxClient
import org.dsa.iot.util.InfluxClient._

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

    case msg: LogResponderConfig => influx.write(msg)

    case msg: LogRequesterConfig => influx.bulkWrite(msg)
  }

  /**
    * Converts a LogResponderConfig instance into an InfluxDB point.
    *
    * @param lrc
    * @return
    */
  implicit protected def logRspCfg2point(lrc: LogResponderConfig) = {
    val baseTags = tags("linkName" -> lrc.linkName, "collateUpdates" -> lrc.cfg.collateAutoIncUpdates.toString)
    val baseFields = fields("nodeCount" -> lrc.cfg.nodeCount)

    val autoInc = lrc.cfg.autoIncInterval.map { to =>
      fields("autoIncMs" -> to.toMillis, "autoInc" -> 1)
    } getOrElse
      fields("autoIncMs" -> 1, "autoInc" -> 0)

    val ts = lrc.ts.getEpochSecond * 1000000000L + lrc.ts.getNano

    Point("rsp_config", ts, baseTags, baseFields ++ autoInc)
  }

  /**
    * Converts a LogRequesterConfig instance into InfluxDB points.
    *
    * @param lrc
    * @return
    */
  implicit protected def logReqCfg2points(lrc: LogRequesterConfig) = {
    val baseTags = tags("linkName" -> lrc.linkName, "subscribe" -> lrc.cfg.subscribe.toString)
    val invokes = lrc.cfg.timeout.map { to =>
      fields("timeoutMs" -> to.toMillis, "invokes" -> 1)
    } getOrElse
      fields("timeoutMs" -> 1, "invokes" -> 0)

    val ts = lrc.ts.getEpochSecond * 1000000000L + lrc.ts.getNano

    lrc.paths.toSeq map { path =>
      Point("req_config", ts,
        baseTags ++ tags("path" -> path),
        fields("dummy" -> -1, "linkId" -> lrc.linkName, "pathId" -> path) ++ invokes)
    }
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
      .addField("dummy", -1)

    msg match {
      case RequestMessage(_, _, requests)   =>
        base.addField("requests", requests.size) +: (requestsToPoints(linkName, linkType, inbound)(requests)).toList
      case ResponseMessage(_, _, responses) =>
        List(base
          .addField("responses", responses.size)
          .addField("updates", responses.map(_.updates.getOrElse(Nil).size).sum)
          .addField("errors", responses.map(_.error).filter(_.isDefined).size))
      case _ if logAuxMessages              =>
        List(base)
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

  val ConfigInstant = Instant.parse("2000-01-01T00:00:00.00Z")

  case class LogInboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: Instant = Instant.now)

  case class LogOutboundMessage(linkName: String, linkType: LinkType, msg: DSAMessage, ts: Instant = Instant.now)

  case class LogResponderConfig(linkName: String, cfg: BenchmarkResponderConfig, ts: Instant = ConfigInstant)

  case class LogRequesterConfig(linkName: String, paths: Iterable[String], cfg: BenchmarkRequesterConfig,
                                ts: Instant = ConfigInstant)

  /**
    * Creates a new StatsCollector props instance.
    *
    * @param influx
    * @param logAuxMessages
    * @return
    */
  def props(influx: InfluxClient, logAuxMessages: Boolean) = Props(new StatsCollector(influx, logAuxMessages))
}