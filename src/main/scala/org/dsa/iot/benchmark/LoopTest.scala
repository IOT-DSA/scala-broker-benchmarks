package org.dsa.iot.benchmark

import java.io.{ FileWriter, PrintWriter }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import scala.util.Random

import org.dsa.iot.dslink.node.value.SubscriptionValue
import org.dsa.iot.dslink.util.handler.Handler
import org.dsa.iot.scala.DSAHelper
import org.slf4j.LoggerFactory
import java.io.File
import org.dsa.iot.dslink.util.SubData

/**
 * Subscribes to a responder's node and then keeps invoking the action that changes that
 * node's value.
 */
object LoopTest extends App {
  import Settings._
  import org.dsa.iot.scala.LinkMode._

  val expectedInvokeRate = Loop.Instances * Loop.BatchSize * 1000.00 / Loop.Timeout.toMillis

  val reportName = "LOOP-%.0f.csv".format(expectedInvokeRate)
  val out = new PrintWriter(new FileWriter(new File(ReportDir, reportName)), true)
  out.println("invokes,cur_inv_rate,glb_inv_rate,exp_inv_rate,events,cur_env_rate,glb_env_rate,exp_env_rate")

  val log = LoggerFactory.getLogger(getClass)
  val stopFlag = new AtomicBoolean(false)

  log.info("Starting {} requesters", Loop.Instances)
  val loops = (1 to Loop.Instances) map (new RequesterLoop(_))

  // calculate the expected ratio events/invokes
  val expectedEventInvokeRatio = {
    val targets = loops.map(lp => (lp.rspIndex, lp.counterIndex))
    val expectedEvents = targets.groupBy(identity).map(_._2.size).map(a => a * a).sum
    expectedEvents * 1.0 / loops.size
  }
  log.info("Expected events/invokes rate is %.0f %%".format(expectedEventInvokeRatio * 100))

  loops foreach (_.start)

  var lastInvokeAndEventCounts: Option[(Int, Int, Long)] = None

  val statsThread = new Thread(new Runnable {
    def run = while (!stopFlag.get) {
      val now = System.currentTimeMillis
      val counts = loops map (_.invokeAndEventCounts)
      val totalInvokes = counts map (_._1) sum
      val totalEvents = counts map (_._2) sum

      lastInvokeAndEventCounts foreach {
        case (lastInvokes, lastEvents, lastTime) =>
          report(totalInvokes, totalEvents, now, lastInvokes, lastEvents, lastTime)
      }

      lastInvokeAndEventCounts = Some(Tuple3(totalInvokes, totalEvents, now))
      Thread.sleep(Loop.ReportTimeout.toMillis)
    }
  })

  val startedAt = System.currentTimeMillis
  statsThread.start

  waitForEnter

  loops foreach (_.stop)

  waitForEnter
  
  loops foreach (_.close)
  stopFlag.set(true)
  statsThread.join

  out.close
  sys.exit

  /**
   * Reports current stats.
   */
  private def report(totalInvokes: Int, totalEvents: Int, now: Long,
                     lastInvokes: Int, lastEvents: Int, lastTime: Long) = {

    val invokes = totalInvokes - lastInvokes
    val currentInvokeRate = rate(invokes, now - lastTime)
    val globalInvokeRate = rate(totalInvokes, now - startedAt)

    val events = totalEvents - lastEvents
    val currentEventRate = rate(events, now - lastTime)
    val expectedEventRate = globalInvokeRate * expectedEventInvokeRatio
    val globalEventRate = rate(totalEvents, now - startedAt)

    log.info("Invokes/s (cur-glb-exp): %.0f-%.0f-%.0f, Events/s (cur-glb-exp): %.0f-%.0f-%.0f".format(
      currentInvokeRate, globalInvokeRate, expectedInvokeRate,
      currentEventRate, globalEventRate, expectedEventRate))

    out.println("%d,%.0f,%.0f,%.0f,%d,%.0f,%.0f,%.0f".format(
      totalInvokes, currentInvokeRate, globalInvokeRate, expectedInvokeRate,
      totalEvents, currentEventRate, globalEventRate, expectedEventRate))
  }

  /**
   * Finds rate/sec.
   */
  private def rate(count: Int, passed: Long) = count * 1000.0 / passed

  /**
   * Subscribes to a data path and starts a thread to periodically invoke the action.
   */
  class RequesterLoop(index: Int) extends Runnable {

    val id = Loop.Id + index
    log.debug("{}: Creating Requester Loop", id)

    val connector = createConnector(id, "/requester.json")
    val connection = connector.start(REQUESTER)
    implicit val requester = connection.requester

    sys.addShutdownHook {
      if (connector.isConnected) connector.stop
    }

    private val stopFlag = new AtomicBoolean(false)
    private val thread = new Thread(this)

    val rspIndex = Random.nextInt(Responder.Instances) + 1
    private val rspId = Responder.Id + rspIndex
    val counterIndex = Random.nextInt(Responder.NodeCount) + 1

    private val subPath = s"/downstream/$rspId/counter$counterIndex/data"
    private val subCounter = new AtomicInteger(0)

    private val invPath = s"/downstream/$rspId/counter$counterIndex/incCounter"
    private val invCounter = new AtomicInteger(0)

    log.info("{}: subscribing to {}", id, subPath)
    requester.subscribe(new SubData(subPath, 3), new Handler[SubscriptionValue] {
      def handle(evt: SubscriptionValue) = {
        subCounter.incrementAndGet
      }
    })

    def invokeAndEventCounts = synchronized((invokeCount, eventCount))

    def eventCount = subCounter.get

    def invokeCount = invCounter.get

    def lag = synchronized(invokeCount - eventCount)

    def start() = {
      thread.start
      log.debug("{}: Requester Loop started", id)
    }

    def stop() = {
      stopFlag.set(true)
      thread.join
      log.debug("{}: Requester Loop stopped", id)
    }
    
    def close() = connector.stop

    def run = while (!stopFlag.get) {
      (1 to Loop.BatchSize) foreach { _ =>
        DSAHelper invoke invPath
        invCounter.incrementAndGet // due to multithreading, to improve reporting accuracy
      }
      if (Loop.Timeout.toMillis > 0)
        Thread sleep Loop.Timeout.toMillis
    }
  }
}