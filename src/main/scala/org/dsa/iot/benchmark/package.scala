//package org.dsa.iot
//
//import java.nio.file.Files
//
//import _root_.scala.concurrent.duration.Duration
//import _root_.scala.util.Random
//
//import org.dsa.iot.dslink.node.Node
//import org.dsa.iot.scala.DSAConnector
//import org.slf4j.LoggerFactory
//
///**
// * Benchmark utilities and helper types.
// */
//package object benchmark {
//  import Settings._
//
//  val log = LoggerFactory.getLogger(getClass)
//
//  /**
//   * Generates a random alphanumeric Id.
//   */
//  def generateId(size: Int = 6) = Random.alphanumeric.take(size).mkString.capitalize
//
//  /**
//   * Creates a DSA Connector for the specified DSLink name and json resource.
//   */
//  def createConnector(name: String, resource: String) = {
//    val dir = Files.createTempDirectory("dsa")
//
//    val dslinkJsonPath = dir.resolve("dslink.json")
//    val nodesJsonPath = dir.resolve("nodes.json")
//    val nodesJsonBakPath = dir.resolve("nodes.json.bak")
//    val keyPath = dir.resolve(".keys")
//    Files.copy(getClass.getResourceAsStream(resource), dslinkJsonPath)
//
//    sys.addShutdownHook {
//      Files.delete(dslinkJsonPath)
//      Files.deleteIfExists(nodesJsonPath)
//      Files.deleteIfExists(nodesJsonBakPath)
//      Files.deleteIfExists(keyPath)
//      Files.delete(dir)
//    }
//
//    log.debug("Connecting to broker at {}", BrokerUrl)
//    DSAConnector.create(brokerUrl = BrokerUrl, nodesPath = Some(nodesJsonPath.toString),
//      keyPath = Some(keyPath.toString), dslinkJsonPath = Some(dslinkJsonPath.toString),
//      dslinkName = Some(name))
//  }
//
//  /**
//   * Waits for Enter.
//   */
//  def waitForEnter() = {
//    println("Press ENTER to continue")
//    System.in.read
//  }
//
//  /**
//   * Waits the specified
//   */
//  def pause(duration: Duration) = Thread.sleep(duration.toMillis)
//
//  /**
//   * Returns node info.
//   */
//  def nodeInfo(node: Node): String = {
//    val name = node.getName
//    val display = node.getDisplayName
//    val path = node.getPath
//    val value = node.getValue
//    val valueType = Option(node.getValueType).map(_.getRawName).orNull
//    val action = Option(node.getAction).map { a =>
//      s"Action(params=${a.getParams.size},type=${a.getResultType},perm=${a.getPermission})"
//    }.orNull
//    val profile = node.getProfile
//    val config = node.getConfigurations
//    val ifaces = node.getInterfaces
//    val attrs = node.getAttributes
//    val roConfig = node.getRoConfigurations
//    val link = Option(node.getLink).map(_ => "yes").orNull
//    val writable = node.getWritable
//    val childCount = Option(node.getChildren) map (_.size) getOrElse 0
//
//    s"""name=$name, display=$display, path=$path, value=$value, type=$valueType, 
//      | action=$action, profile=$profile, configs=$config,
//      | ifaces=$ifaces, attrs=$attrs, roConfigs=$roConfig,
//      | link=$link, writable=$writable, children=$childCount""".stripMargin.replaceAll("\n", "")
//  }
//}