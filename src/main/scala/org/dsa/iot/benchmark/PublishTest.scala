//package org.dsa.iot.benchmark
//
//import scala.util.Random
//
//import org.dsa.iot.dslink.link.Requester
//import org.dsa.iot.scala.DSAHelper
//import org.slf4j.LoggerFactory
//
///**
// * Usage: PublishTest [linkId]
// */
//object PublishTest extends App {
//  import Settings._
//  import org.dsa.iot.scala.LinkMode._
//
//  val log = LoggerFactory.getLogger(getClass)
//
//  log.info("Starting Publisher Test")
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
//  val thread1 = new Thread {
//    override def run = try {
//      while (true) {
//        benchmarkSetAttribute(Publish.AttributeBatchSize)(requester)
//        pause(Publish.AttributeTimeout)
//      }
//    } catch {
//      case e: InterruptedException => log.info("Thread interruped")
//    }
//  }
//  
//  val thread2 = new Thread {
//    override def run = try {
//      while (true) {
//        benchmarkSetValue(Publish.ValueBatchSize)(requester)
//        pause(Publish.ValueTimeout)
//      }
//    } catch {
//      case e: InterruptedException => log.info("Thread interruped")
//    }
//  }  
//  
//  thread1.start
//  thread2.start
//  
//  /**
//   * Executes a batch of SET commands over attributes and returns the average execution time.
//   */
//  private def benchmarkSetAttribute(iterations: Int)(implicit requester: Requester) = {
//    val start = System.currentTimeMillis
//    (1 to iterations) foreach { _ =>
//      val idx = Random.nextInt(Responder.AttributeCount) + 1
//      val path = s"/downstream/${Responder.Id}/data/@item$idx"
//      DSAHelper set (path, Random.nextInt(1000))
//    }
//    val end = System.currentTimeMillis
//
//    (end - start) / iterations.toDouble
//  }
//  
//  /**
//   * Executes a batch of SET commands over node value and returns the average execution time.
//   */
//  private def benchmarkSetValue(iterations: Int)(implicit requester: Requester) = {
//    val start = System.currentTimeMillis
//    (1 to iterations) foreach { _ =>
//      val path = s"/downstream/${Responder.Id}/data"
//      DSAHelper set (path, Random.nextInt(1000))
//    }
//    val end = System.currentTimeMillis
//
//    (end - start) / iterations.toDouble
//  }  
//}