package org.dsa.iot.ws

import java.net.URL

import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.bouncycastle.jcajce.provider.digest.SHA256
import org.dsa.iot.actors.LinkType
import org.dsa.iot.handshake.{LocalKeys, RemoteKey, UrlBase64}
import org.dsa.iot.rpc.DSAMessage
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

/**
  * Establishes a connection with a DSA Broker.
  */
class WebSocketConnector(keys: LocalKeys)(implicit system: ActorSystem, materializer: ActorMaterializer)
  extends PlayJsonSupport {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val connReqFormat = Json.format[ConnectionRequest]

  implicit private val ec = system.dispatcher

  /**
    * Connects to a DSA broker and returns a connection instance.
    */
  def connect(dslinkName: String, brokerUrl: String, dslinkType: LinkType,
              propsFunc: ActorRef => Props) = {
    val connUrl = new URL(brokerUrl)

    val dsId = dslinkName + "-" + keys.encodedHashedPublicKey

    val connReq = ConnectionRequest(keys.encodedPublicKey, dslinkType.isRequester, dslinkType.isResponder,
      None, "1.1.2", Some(List("json")), true)

    val uri = Uri(brokerUrl).withQuery(Uri.Query("dsId" -> dsId))

    val fBody = Marshal(connReq).to[RequestEntity]
    val frsp = fBody.flatMap { body =>
      log.debug("Connecting to {}", brokerUrl)
      val req = HttpRequest(method = HttpMethods.POST, uri = uri, entity = body)
      Http().singleRequest(req)
    }

    frsp flatMap (rsp => Unmarshal(rsp.entity).to[JsValue]) flatMap { serverConfig =>
      val tempKey = (serverConfig \ "tempKey").as[String]
      val wsUri = (serverConfig \ "wsUri").as[String]
      val salt = (serverConfig \ "salt").as[String].getBytes("UTF-8")

      val auth = buildAuth(tempKey, salt)
      val wsUrl = s"ws://${connUrl.getHost}:${connUrl.getPort}$wsUri?dsId=$dsId&auth=$auth&format=json"

      wsConnect(wsUrl, dslinkName, dsId, propsFunc)
    }
  }

  /**
    * Establishes a websocket connection with a dsa broker.
    */
  private def wsConnect(url: String, name: String, dsId: String, propsFunc: ActorRef => Props) = {
    val (dsaFlow, wsActor) = createWSFlow(propsFunc)

    val inFlow = Flow[Message].collect {
      case TextMessage.Strict(s) => Json.parse(s).as[DSAMessage]
    }
    val wsFlow = inFlow.viaMat(dsaFlow)(Keep.right).map(msg => TextMessage.Strict(Json.toJson(msg).toString))

    val (upgradeResponse, _) = Http().singleWebSocketRequest(WebSocketRequest(url), wsFlow)

    upgradeResponse.map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        log.info(s"[$name]: web socket connection established to {}", url)
        DSAConnection(wsActor)
      } else
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  /**
    * Creates a new WebSocket flow bound to a newly created WSActor.
    */
  private def createWSFlow(propsFunc: ActorRef => Props,
                           bufferSize: Int = 16,
                           overflow: OverflowStrategy = OverflowStrategy.dropNew) = {
    import akka.actor.Status._

    val (toSocket, publisher) = Source.actorRef[DSAMessage](bufferSize, overflow)
      .toMat(Sink.asPublisher(false))(Keep.both).run()(materializer)

    val fromSocket = system.actorOf(Props(new Actor {
      val wsActor = context.watch(context.actorOf(propsFunc(toSocket), "wsActor"))

      def receive = {
        case Success(_) | Failure(_) => wsActor ! PoisonPill
        case Terminated(_)           => context.stop(self)
        case other                   => wsActor ! other
      }

      override def supervisorStrategy = OneForOneStrategy() {
        case _ => SupervisorStrategy.Stop
      }
    }))

    val flow = Flow.fromSinkAndSource[DSAMessage, DSAMessage](
      Sink.actorRef(fromSocket, Success(())),
      Source.fromPublisher(publisher))

    (flow, fromSocket)
  }

  /**
    * Builds the authorization hash to be sent to the remote broker.
    */
  private def buildAuth(tempKey: String, salt: Array[Byte]) = {
    val remoteKey = RemoteKey.generate(keys, tempKey)
    val sharedSecret = remoteKey.sharedSecret
    // TODO make more scala-like
    val bytes = Array.ofDim[Byte](salt.length + sharedSecret.length)
    System.arraycopy(salt, 0, bytes, 0, salt.length)
    System.arraycopy(sharedSecret, 0, bytes, salt.length, sharedSecret.length)

    val sha = new SHA256.Digest
    val digested = sha.digest(bytes)
    UrlBase64.encodeBytes(digested)
  }
}
