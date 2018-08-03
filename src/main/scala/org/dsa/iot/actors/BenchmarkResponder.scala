package org.dsa.iot.actors

import akka.actor.{ActorRef, Cancellable, Props}
import org.dsa.iot.rpc.DSAValue._
import org.dsa.iot.rpc.StreamState._
import org.dsa.iot.rpc._
import org.dsa.iot.util.{EnvUtils, InfluxClient, SimpleCache}
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.util.Random

/**
  * A sample responder that creates a list of nodes with names data1, data2, etc. with the initial value of 0.
  *
  * Each node defines two actions - incCount, which increments the node value, and resetCounter,
  * that resets the node's value to 0.
  *
  * The responder supports the following operations:
  * - list (/ and /dataX)
  * - set (/dataX)
  * - invoke (/dataX/incCounter and /dataX/resetCounter)
  * - subscribe (/dataX)
  * - unsubscribe (/dataX)
  */
class BenchmarkResponder(linkName: String, out: ActorRef, influx: InfluxClient, cfg: BenchmarkResponderConfig)
  extends WebSocketActor(linkName, LinkType.Responder, out, influx, cfg) {

  import BenchmarkResponder._
  import context.dispatcher

  private type Action = () => Seq[DSAResponse]

  private val data = Array.fill(cfg.nodeCount)(0)
  private val subscriptions = collection.mutable.Map.empty[String, Int]

  private val actionCache = new SimpleCache[String, Action](100, 1)

  private var autoIncJob: Cancellable = _

  /**
    * Schedules the auto-increment job.
    */
  override def preStart: Unit = {
    log.debug("{}: scheduling auto-increments at {}", linkName, cfg.autoIncrementInterval)
    autoIncJob = context.system.scheduler.schedule(cfg.autoIncrementInterval, cfg.autoIncrementInterval, self, AutoIncTick)

    super.preStart
  }

  /**
    * Stops the auto-increment job.
    */
  override def postStop: Unit = {
    log.debug("{}: canceling auto-increments", linkName)
    autoIncJob.cancel

    super.postStop
  }

  /**
    * Handles incoming messages.
    */
  override def receive: Receive = super.receive orElse {
    case msg: RequestMessage =>
      log.debug("{}: received {}", linkName, msg)
      influx.write(msg)(msg2point(true))
      val responses = msg.requests flatMap processRequest
      sendToSocket(ResponseMessage(localMsgId.inc, None, responses))

    case AutoIncTick =>
      log.debug("{}: auto-incrementing {} nodes", linkName, cfg.autoIncrementNodes)
      val updates = (1 to cfg.autoIncrementNodes) flatMap { _ =>
        val index = Random.nextInt(cfg.nodeCount)
        incCounter(index + 1)
      } flatMap (_.updates.getOrElse(Nil))
      val response = DSAResponse(0, Some(Open), Some(updates.toList))
      sendToSocket(ResponseMessage(localMsgId.inc, None, List(response)))

    case msg => log.warning("{}: received unknown message - {}", linkName, msg)
  }

  /**
    * Processes a single [[DSARequest]] and returns a list of responses.
    */
  private def processRequest: PartialFunction[DSARequest, Seq[DSAResponse]] = {
    case ListRequest(rid, "/") =>
      List(processRootListRequest(rid))

    case ListRequest(rid, path) if path.matches("/data(\\d+)") =>
      List(processDataListRequest(rid))

    case ListRequest(rid, _) =>
      List(emptyResponse(rid))

    case SetRequest(rid, path, x: NumericValue, _) if path.startsWith("/data") =>
      processSetRequest(rid, path, x.value)

    case SubscribeRequest(rid, paths) =>
      val updates = paths flatMap { path =>
        subscriptions += path.path -> path.sid
        val index = path.path.drop(5).toInt
        notifySubs(index)
      }
      emptyResponse(rid) :: updates

    case UnsubscribeRequest(rid, sids) =>
      val keys = subscriptions.collect {
        case (path, sid) if sids.contains(sid) => path
      }
      subscriptions --= keys
      List(emptyResponse(rid))

    case req: InvokeRequest => processInvokeRequest(req)

    case CloseRequest(rid) =>
      log.debug("{}: closing request {}", linkName, rid)
      List(emptyResponse(rid))
  }

  /**
    * Outputs responder statistics.
    */
  protected def reportStats(): Unit = {
    log.debug("{}: TODO: reporting stats", linkName)
  }

  /**
    * Handles LIST / request.
    *
    * @param rid
    * @return
    */
  private def processRootListRequest(rid: Int) = {
    val configs = rows(isNode)
    val children = (1 to cfg.nodeCount) map (index => array("data" + index, obj(isNode)))

    DSAResponse(rid, Some(Closed), Some(configs ++ children))
  }

  /**
    * Handles LIST /dataX request.
    *
    * @param rid
    * @return
    */
  private def processDataListRequest(rid: Int) = {
    val configs = rows(isNode, "$writable" -> "write", "$type" -> "number", "$editor" -> "none")
    val actions = List(
      array("incCounter", obj("$is" -> "node", "$name" -> "Inc Counter", "$invokable" -> "write")),
      array("resetCounter", obj("$is" -> "node", "$name" -> "Reset Counter", "$invokable" -> "write")))

    DSAResponse(rid, Some(Closed), Some(configs ++ actions))
  }

  /**
    * Handles SET /dataX value request.
    *
    * @param rid
    * @param path
    * @param value
    * @return
    */
  private def processSetRequest(rid: Int, path: String, value: Number) = {
    val index = path.drop(5).toInt
    data(index - 1) = value.intValue
    emptyResponse(rid) +: notifySubs(index)
  }

  /**
    * Handles INVOKE /dataX/resetCounter and /dataX/incCounter requests.
    *
    * @param req
    * @return
    */
  private def processInvokeRequest(req: InvokeRequest) = {
    val action = actionCache.getOrElseUpdate(req.path, createAction(req.path))
    replyToInvoke(req) +: action()
  }

  /**
    * Generates a response to INVOKE request.
    *
    * @param req
    * @return
    */
  private def replyToInvoke(req: InvokeRequest) = emptyResponse(req.rid)

  /**
    * Creates either `incCounter` or `resetCounter` action depending on the path.
    *
    * @param path
    * @return
    */
  private def createAction(path: String): Action = path match {
    case r"/data(\d+)$index/incCounter"   => new Action {
      def apply = incCounter(index.toInt)
    }
    case r"/data(\d+)$index/resetCounter" => new Action {
      def apply = resetCounter(index.toInt)
    }
  }

  /**
    * Increments the value of the specified data node.
    *
    * @param index
    * @return
    */
  private def incCounter(index: Int) = {
    data(index - 1) += 1
    notifySubs(index)
  }

  /**
    * Resets the value of the specified data node.
    *
    * @param index
    * @return
    */
  private def resetCounter(index: Int) = {
    data(index - 1) = 0
    notifySubs(index)
  }

  /**
    * Creates a response with subscription update about a value change of the specified node.
    *
    * @param index
    * @return
    */
  private def notifySubs(index: Int) = subscriptions.get("/data" + index) map { sid =>
    val update = obj("sid" -> sid, "value" -> data(index - 1), "ts" -> DateTime.now.toString)
    DSAResponse(0, Some(Open), Some(List(update)))
  } toSeq

  /**
    * Creates an empty DSAResponse with the specified RID.
    *
    * @param rid
    * @return
    */
  private def emptyResponse(rid: Int) = DSAResponse(rid, Some(StreamState.Closed))

  /**
    * Builds a list of rows, each containing two values.
    *
    * @param pairs
    * @return
    */
  def rows(pairs: (String, DSAVal)*) = pairs map {
    case (key, value) => array(key, value)
  } toList

  /**
    * Creates a tuple for \$is config.
    *
    * @param str
    * @return
    */
  def is(str: String): (String, StringValue) = "$is" -> StringValue(str)

  /**
    * A constant for "$is" -> "node" tuple.
    */
  private val isNode = is("node")
}

/**
  * Factory for [[BenchmarkResponder]] instances.
  */
object BenchmarkResponder {

  /**
    * Sent by scheduler to initiate auto-increment actions.
    */
  case object AutoIncTick

  /**
    * Creates a new BenchmarkResponder props.
    */
  def props(linkName: String, out: ActorRef, influx: InfluxClient,
            cfg: BenchmarkResponderConfig = EnvBenchmarkResponderConfig) =
    Props(new BenchmarkResponder(linkName, out, influx, cfg))
}

/**
  * BenchmarkResponder configuration.
  */
trait BenchmarkResponderConfig extends WebSocketActorConfig {

  /**
    * @return children node count.
    */
  def nodeCount: Int

  /**
    * @return interval for incCounter auto-invoke
    */
  def autoIncrementInterval: FiniteDuration

  /**
    * @return node count to auto invoke incCounter on each time [[autoIncrementInterval]] passes.
    */
  def autoIncrementNodes: Int
}

/**
  * BenchmarkResponderConfig implementation based on environment properties.
  */
object EnvBenchmarkResponderConfig extends EnvWebSocketActorConfig with BenchmarkResponderConfig {

  override def nodeCount: Int = EnvUtils.getInt("responder.nodes", 10)

  override def autoIncrementInterval: FiniteDuration =
    EnvUtils.getMillis("responder.autoinc.interval", 1 second)

  override def autoIncrementNodes: Int = EnvUtils.getInt("responder.autoinc.nodes", 10)
}