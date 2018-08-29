package org.dsa.iot.actors

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.Point
import org.dsa.iot.actors.StatsCollector._
import org.dsa.iot.rpc.DSAMethod.{Subscribe, Unsubscribe}
import org.dsa.iot.rpc._
import org.dsa.iot.util.InfluxClient
import org.dsa.iot.util.InfluxClient._

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Collects messages from benchmark actors and writes statistics to InfluxDB.
  *
  * @param influx
  * @param logAuxMessages
  * @param writeInterval if 'Duration.Zero' then data is written right away, otherwise it is bundled up
  *                      and sent at this interval.
  */
class StatsCollector(influx: InfluxClient, logAuxMessages: Boolean, writeInterval: FiniteDuration)
  extends Actor with ActorLogging {

  import context.dispatcher

  implicit val precision = Precision.NANOSECONDS

  private var lastNanos: Long = 0L

  private var writeJob: Option[Cancellable] = None

  private var points: Seq[Point] = Nil

  /**
    * Schedules a job to write data to InfluxDB.
    */
  override def preStart(): Unit = {
    writeJob = if (writeInterval != Duration.Zero)
      Some(context.system.scheduler.schedule(writeInterval, writeInterval, self, WriteTick))
    else
      None

    log.info("StatsCollector started with write interval {}", writeInterval)
  }

  /**
    * Stops the write-to-influxdb job.
    */
  override def postStop(): Unit = {
    writeJob foreach (_.cancel())
    log.info("StatsCollector stopped")
  }

  /**
    * Handles incoming messages.
    */
  def receive: Receive = {
    case msg: LogInboundMessage =>
      points ++= logInbound2points(msg)
      writeToDbIfNoDelay()

    case msg: LogOutboundMessage =>
      points ++= logOutbound2points(msg)
      writeToDbIfNoDelay()

    case msg: LogResponderConfig =>
      points :+= logRspCfg2point(msg)
      doWriteToDb

    case msg: LogRequesterConfig =>
      points ++= logReqCfg2points(msg)
      doWriteToDb

    case WriteTick =>
      doWriteToDb()
  }

  /**
    * Converts a LogResponderConfig instance into an InfluxDB point.
    *
    * @param lrc
    * @return
    */
  protected def logRspCfg2point(lrc: LogResponderConfig) = {
    val baseTags = tags("linkName" -> lrc.linkName, "collateUpdates" -> lrc.cfg.collateAutoIncUpdates.toString)
    val baseFields = fields("nodeCount" -> lrc.cfg.nodeCount)

    val autoInc = lrc.cfg.autoIncInterval.map { to =>
      fields("autoIncMs" -> to.toMillis, "autoInc" -> 1)
    } getOrElse
      fields("autoIncMs" -> 1, "autoInc" -> 0)

    val ts = toNanos(lrc.ts)

    Point("rsp_config", ts, baseTags, baseFields ++ autoInc)
  }

  /**
    * Converts a LogRequesterConfig instance into InfluxDB points.
    *
    * @param lrc
    * @return
    */
  protected def logReqCfg2points(lrc: LogRequesterConfig) = {
    val baseTags = tags("linkName" -> lrc.linkName, "subscribe" -> lrc.cfg.subscribe.toString)
    val invokes = lrc.cfg.timeout.map { to =>
      fields("timeoutMs" -> to.toMillis, "invokes" -> 1)
    } getOrElse
      fields("timeoutMs" -> 1, "invokes" -> 0)

    val ts = toNanos(lrc.ts)

    lrc.paths.toSeq map { path =>
      Point("req_config", ts,
        baseTags ++ tags("path" -> path),
        fields("dummy" -> -1, "linkId" -> lrc.linkName, "pathId" -> path) ++ invokes)
    }
  }

  /**
    * Converts a LogInboundMessage instance into InfluxDB points.
    *
    * @param lim
    * @return
    */
  protected def logInbound2points(lim: LogInboundMessage) =
    message2points(lim.linkName, lim.linkType, true, lim.ts)(lim.msg)

  /**
    * Converts a LogOutboundMessage instance into InfluxDB points.
    *
    * @param lom
    * @return
    */
  protected def logOutbound2points(lom: LogOutboundMessage) =
    message2points(lom.linkName, lom.linkType, false, lom.ts)(lom.msg)

  /**
    * Converts a DSAMessage with context parameters into InfluxDB points.
    *
    * @param linkName
    * @param linkType
    * @param inbound
    * @param ts
    * @param msg
    * @return
    */
  private def message2points(linkName: String, linkType: LinkType, inbound: Boolean, ts: Instant)
                            (msg: DSAMessage) = {

    val current = toNanos(ts)
    lastNanos = if (current > lastNanos) current else lastNanos + 1

    val base = Point("message", lastNanos)
      .addTag("linkName", linkName)
      .addTag("linkType", linkType.toString)
      .addTag("msgType", msg.getClass.getSimpleName)
      .addTag("inbound", inbound.toString)
      .addField("dummy", -1)

    msg match {
      case RequestMessage(_, _, requests)   =>
        base.addField("requests", requests.size) +: (requestsToPoints(linkName, linkType, inbound, lastNanos)(requests)).toList
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
    * @param nanos
    * @return
    */
  private def requestsToPoints(linkName: String, linkType: LinkType, inbound: Boolean, nanos: Long)
                              (requests: Iterable[DSARequest]) = {

    val base = Point("request", nanos)
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

  /**
    * Writes points to InfluxDB if there should be no write batching.
    */
  private def writeToDbIfNoDelay() = if (writeInterval == Duration.Zero) doWriteToDb()

  /**
    * Writes point to InfluxDB.
    */
  private def doWriteToDb() = {
    influx.bulkWrite(points)
    points = Nil
  }

  /**
    * Converts an instant into the number of nanoseconds since epoch.
    *
    * @param ts
    * @return
    */
  private def toNanos(ts: Instant) = ts.getEpochSecond * 1000000000L + ts.getNano
}

/**
  * Factory for [[StatsCollector]] instances.
  */
object StatsCollector {

  /**
    * Sent by scheduler to initiate writes to InfluxDB.
    */
  case object WriteTick

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
    * @param writeInterval
    * @return
    */
  def props(influx: InfluxClient, logAuxMessages: Boolean, writeInterval: FiniteDuration) =
    Props(new StatsCollector(influx, logAuxMessages, writeInterval))
}