package org.dsa.iot.actors

import org.dsa.iot.rpc._
import org.dsa.iot.util.SimpleCache
import org.joda.time.DateTime

import akka.actor.{ ActorRef, Props }

/**
 * A sample responder that creates a list of nodes with names data0, data1, etc. with the initial value of 0.
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
class BenchmarkResponder(linkName: String, nodeCount: Int, out: ActorRef)
  extends AbstractWebSocketActor(linkName, true, false, out) {

  import org.dsa.iot.rpc.DSAValue._
  import org.dsa.iot.rpc.StreamState._

  private type Action = Function0[Seq[DSAResponse]]

  private val data = Array.fill(nodeCount)(0)
  private val subscriptions = collection.mutable.Map.empty[String, Int]

  private val actionCache = new SimpleCache[String, Action](100, 1)

  /**
   * Handles incoming messages of [[RequestMessage]] type.
   */
  override def receive = super.receive orElse {
    case msg: RequestMessage =>
      log.debug("{}: received {}", linkName, msg)
      val responses = msg.requests flatMap processRequest
      sendToSocket(ResponseMessage(localMsgId.inc, None, responses))

    case msg => log.warning("{}: received unknown message - {}", linkName, msg)
  }

  /**
   * Process a single [[DSARequest]] and returns a list of responses.
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
      paths foreach { path =>
        subscriptions += path.path -> path.sid
      }
      List(emptyResponse(rid))

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
   * Handles LIST / request.
   */
  private def processRootListRequest(rid: Int) = {
    val configs = rows(isNode)
    val children = (1 to nodeCount) map (index => array("data" + index, obj(isNode)))

    DSAResponse(rid, Some(Closed), Some(configs ++ children))
  }

  /**
   * Hanldes LIST /dataX request.
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
   */
  private def processSetRequest(rid: Int, path: String, value: Number) = {
    val index = path.drop(5).toInt
    data(index - 1) = value.intValue
    emptyResponse(rid) +: notifySubs(index)
  }

  /**
   * Handles INVOKE /dataX/resetCounter and /dataX/incCounter requests.
   */
  private def processInvokeRequest(req: InvokeRequest) = {
    val action = actionCache.getOrElseUpdate(req.path, createAction(req.path))
    replyToInvoke(req) +: action()
  }

  /**
   * Generates a response to INVOKE request.
   */
  private def replyToInvoke(req: InvokeRequest) = emptyResponse(req.rid)

  /**
   * Creates either `incCounter` or `resetCounter` action depending on the path.
   */
  private def createAction(path: String): Action = path match {
    case r"/data(\d+)$index/incCounter" => new Action {
      def apply = incCounter(index.toInt)
    }
    case r"/data(\d+)$index/resetCounter" => new Action {
      def apply = resetCounter(index.toInt)
    }
  }

  /**
   * Increments the value of the specified data node.
   */
  private def incCounter(index: Int) = {
    data(index - 1) += 1
    notifySubs(index)
  }

  /**
   * Resets the value of the specified data node.
   */
  private def resetCounter(index: Int) = {
    data(index - 1) = 0
    notifySubs(index)
  }

  /**
   * Notifies the subscribers about a value change of the specified node.
   */
  private def notifySubs(index: Int) = subscriptions.get("/data" + index) map { sid =>
    val update = obj("sid" -> sid, "value" -> data(index - 1), "ts" -> DateTime.now.toString)

    DSAResponse(0, Some(Open), Some(List(update)))
  } toSeq

  /**
   * Creates an empty DSAResponse with the specified RID.
   */
  private def emptyResponse(rid: Int) = DSAResponse(rid, Some(StreamState.Closed))

  /**
   * Builds a list of rows, each containing two values.
   */
  def rows(pairs: (String, DSAVal)*) = pairs map {
    case (key, value) => array(key, value)
  } toList

  /**
   * Creates a tuple for \$is config.
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
   * Creates a new BenchmarkResponder props.
   */
  def props(linkName: String, nodeCount: Int, out: ActorRef) = Props(new BenchmarkResponder(linkName, nodeCount, out))
}