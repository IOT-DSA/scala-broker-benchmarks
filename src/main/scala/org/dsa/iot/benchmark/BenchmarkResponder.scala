package org.dsa.iot.benchmark

import org.dsa.iot.dslink.link.Responder
import org.dsa.iot.scala._
import org.slf4j.LoggerFactory

/**
 * Starts a sample responder for testing.
 */
object BenchmarkResponder extends App {
  import Settings.Responder._
  import org.dsa.iot.dslink.node.value.ValueType._
  import org.dsa.iot.scala.LinkMode._
  import org.dsa.iot.dslink.node.Writable._

  val log = LoggerFactory.getLogger(getClass)

  log.info("Starting {} responders", Instances)
  (1 to Instances) foreach startResponder
  
  waitForEnter
  sys.exit

  /**
   * Creates and starts a responder.
   */
  private def startResponder(index: Int) = {
    val id = Id + index
    log.info("Starting Responder {}", id)

    val connector = createConnector(id, "/responder.json")
    val connection = connector.start(RESPONDER)
    implicit val responder = connection.responder

    prepareResponder(responder, NodeCount, AttributeCount)

    sys.addShutdownHook {
      connector.stop
    }
  }

  /**
   * Prepares responder data.
   */
  private def prepareResponder(responder: Responder, nodeCount: Int, attributeCount: Int) = {
    val root = responder.getDSLink.getNodeManager.getSuperRoot
    root createChild "str" display "String" valueType STRING value "abc" build ()
    root createChild "bln" display "Boolean" valueType BOOL value true build ()

    (1 to nodeCount) foreach { idx =>
      val counter = root createChild s"counter$idx" display s"Counter$idx" build ()
      val data = counter createChild "data" display "Data" valueType NUMBER value 0 build ()
      counter createChild "incCounter" display "Increment counter" action (event => {
        data.synchronized {
          val oldVal = data.getValue: Int
          val newVal = oldVal + 1
          data.setValue(newVal)
          log.debug(s"Counter$idx changed by ACTION[incCounter] to: " + newVal)
        }
      }) build ()
    }

    root removeChild "data"
    val data = root createChild "data" display "Data" valueType NUMBER value 0 writable WRITE build ()
    (1 until attributeCount) foreach { idx =>
      data.setAttribute(s"item$idx", idx)
    }
  }
}