package org.dsa.iot.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import com.paulgoldbaum.influxdbclient.Point
import org.dsa.iot.rpc._
import org.dsa.iot.util.{EnvUtils, InfluxClient}

import scala.concurrent.duration._

/**
  * Base class for benchmark endpoint actors.
  */
abstract class WebSocketActor(linkName: String, linkType: LinkType, out: ActorRef, influx: InfluxClient,
                              cfg: WebSocketActorConfig) extends Actor with ActorLogging {

  import WebSocketActor._
  import context.dispatcher

  protected val localMsgId = new IntCounter(1)

  protected val scheduler = context.system.scheduler

  private var statsJob: Cancellable = _

  /**
    * Schedules a stats job.
    */
  override def preStart: Unit = {
    log.debug("[{}]: scheduling stats reporting at {}", linkName, cfg.statsInterval)
    statsJob = scheduler.schedule(cfg.statsInterval, cfg.statsInterval, self, StatsTick)

    log.info("[{}]: started", linkName)
  }

  /**
    * Stops the stats job.
    */
  override def postStop: Unit = {
    log.debug("[{}]: canceling stats scheduler", linkName)
    statsJob.cancel

    log.info("[{}]: stopped", linkName)
  }

  /**
    * Handles incoming messages.
    *
    * @return
    */
  def receive: Receive = {
    case m @ EmptyMessage        =>
      log.debug("[{}]: received empty message from WebSocket, ignoring...", linkName)
      reportInboundMessage(m)
    case m @ PingMessage(msg, _) =>
      log.debug("[{}]: received ping from WebSocket with msg={}, acking...", linkName, msg)
      sendAck(msg)
      reportInboundMessage(m)
    case StatsTick               =>
      reportStats
  }

  /**
    * Overridden by subclasses to report stats data.
    */
  protected def reportStats(): Unit = {
    log.debug("[{}]: TODO: reporting stats", linkName)
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
    reportOutboundMessage(msg)
  }

  /**
    * Reports inbound message statistics.
    *
    * @param msg
    * @return
    */
  protected def reportInboundMessage(msg: DSAMessage) =
    influx.bulkWrite(msg)(message2points(true))

  /**
    * Reports outbound message statistics.
    *
    * @param msg
    * @return
    */
  protected def reportOutboundMessage(msg: DSAMessage) =
    influx.bulkWrite(msg)(message2points(false))

  /**
    * Converts a DSAMessage instance into an InfluxDB point.
    *
    * @param inbound
    * @param msg
    * @return
    */
  protected def message2points(inbound: Boolean)(msg: DSAMessage) = {
    val base = Point("message")
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
      case _                                =>
        List(base.addField("dummy", -1))
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
trait WebSocketActorConfig {
  /**
    * @return statistics reporting interval.
    */
  def statsInterval: FiniteDuration
}

/**
  * WebSocketActorConfig implementation based on environment properties.
  */
abstract class EnvWebSocketActorConfig extends WebSocketActorConfig {
  val statsInterval = EnvUtils.getMillis("stats.interval", 10 seconds)
}