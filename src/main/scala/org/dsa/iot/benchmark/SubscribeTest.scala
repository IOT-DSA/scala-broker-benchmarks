package org.dsa.iot.benchmark

import java.util.concurrent.atomic.AtomicLong

import scala.util.Random

import org.dsa.iot.dslink.node.value.SubscriptionValue
import org.dsa.iot.dslink.util.handler.Handler
import org.dsa.iot.scala.DSAHelper
import org.slf4j.LoggerFactory

/**
 * Usage: SubscribeTest [linkId]
 */
object SubscribeTest extends App {
  import Settings._
  import org.dsa.iot.scala.LinkMode._

  val log = LoggerFactory.getLogger(getClass)

  log.info("Starting Invoker Test")

  val id = args.headOption getOrElse Random.alphanumeric.take(5).mkString.capitalize
  val connector = createConnector(id, "/requester.json")
  val connection = connector.start(REQUESTER)
  implicit val requester = connection.requester

  sys.addShutdownHook {
    connector.stop
  }

  val start = System.currentTimeMillis
  val eventCount = new AtomicLong(0)
  val path = s"/downstream/${Responder.Id}/data"
  requester.subscribe(path, new Handler[SubscriptionValue] {
    def handle(evt: SubscriptionValue) = {}
  })

  val thread = new Thread {
    override def run = try {
      while (true) {
        (1 to Subscribe.BatchSize) foreach { _ =>
          DSAHelper set (path, Random.nextInt(1000))
        }
        pause(Subscribe.Timeout)
      }
    } catch {
      case e: InterruptedException => log.info("Thread interruped")
    }
  }
  thread.start
}