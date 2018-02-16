//package org.dsa.iot.benchmark
//
//import scala.util.Random
//
//import org.dsa.iot.dslink.link.Requester
//import org.dsa.iot.scala.DSAHelper
//import org.slf4j.LoggerFactory
//
///**
// * Usage: InvokeTest [linkId]
// */
//object InvokeTest extends App {
//  import Settings._
//  import org.dsa.iot.scala.LinkMode._
//
//  val log = LoggerFactory.getLogger(getClass)
//
//  log.info("Starting Invoker Test")
//
//  val id = args.headOption getOrElse Random.alphanumeric.take(5).mkString.capitalize
//  val connector = createConnector(id, "/requester.json")
//  val connection = connector.start(REQUESTER)
//  implicit val requester = connection.requester
//
//  sys.addShutdownHook {
//    connector.stop
//  }
//
//  val thread = new Thread {
//    override def run = try {
//      while (true) {
//        benchmarkInvoke(Invoke.BatchSize)(requester)
//        pause(Invoke.Timeout)
//      }
//    } catch {
//      case e: InterruptedException => log.info("Thread interruped")
//    }
//  }
//  thread.start
//
//  /**
//   * Executes a batch of INVOKE commands and returns the average execution time per command.
//   */
//  private def benchmarkInvoke(iterations: Int)(implicit requester: Requester) = {
//    val start = System.currentTimeMillis
//    (1 to iterations) foreach { _ =>
//      val idx = Random.nextInt(Responder.NodeCount) + 1
//      val path = s"/downstream/${Responder.Id}/counter$idx/incCounter"
//      DSAHelper invoke path
//    }
//    val end = System.currentTimeMillis
//
//    (end - start) / iterations.toDouble
//  }
//}