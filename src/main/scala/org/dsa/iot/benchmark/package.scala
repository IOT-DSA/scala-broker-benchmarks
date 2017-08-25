package org.dsa.iot

import java.nio.file.Files

import _root_.scala.util.Random
import _root_.scala.concurrent.duration.Duration

import org.dsa.iot.scala.DSAConnector
import org.slf4j.LoggerFactory

/**
 * Benchmark utilities and helper types.
 */
package object benchmark {
  import Settings._

  val log = LoggerFactory.getLogger(getClass)

  /**
   * Generates a random alphanumeric Id.
   */
  def generateId(size: Int = 6) = Random.alphanumeric.take(size).mkString.capitalize

  /**
   * Creates a DSA Connector for the specified DSLink name and json resource.
   */
  def createConnector(name: String, resource: String) = {
    val dir = Files.createTempDirectory("dsa")

    val dslinkJsonPath = dir.resolve("dslink.json")
    val nodesJsonPath = dir.resolve("nodes.json")
    val nodesJsonBakPath = dir.resolve("nodes.json.bak")
    val keyPath = dir.resolve(".keys")
    Files.copy(getClass.getResourceAsStream(resource), dslinkJsonPath)

    sys.addShutdownHook {
      Files.delete(dslinkJsonPath)
      Files.deleteIfExists(nodesJsonPath)
      Files.deleteIfExists(nodesJsonBakPath)
      Files.deleteIfExists(keyPath)
      Files.delete(dir)
    }

    log.debug("Connecting to broker at {}", BrokerUrl)
    DSAConnector.create(brokerUrl = BrokerUrl, nodesPath = Some(nodesJsonPath.toString),
      keyPath = Some(keyPath.toString), dslinkJsonPath = Some(dslinkJsonPath.toString),
      dslinkName = Some(name))
  }

  /**
   * Waits for Enter.
   */
  def waitForEnter() = {
    println("Press ENTER to continue")
    System.in.read
  }

  /**
   * Waits the specified
   */
  def pause(duration: Duration) = Thread.sleep(duration.toMillis)
}