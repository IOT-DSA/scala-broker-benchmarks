// properties
val APP_VERSION = "0.1.0-SNAPSHOT"
val SCALA_VERSION = "2.11.8"
val SCALA_DSA_VERSION = "0.4.0"

// settings
name := "scala-broker-benchmarks"
organization := "org.iot-dsa"
version := APP_VERSION
scalaVersion := SCALA_VERSION

// building
resolvers += Resolver.bintrayRepo("cakesolutions", "maven")
scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Xlint", 
	"-Ywarn-dead-code", "-language:_", "-target:jvm-1.7", "-encoding", "UTF-8")
run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run))

// packaging
enablePlugins(JavaAppPackaging)
mainClass in Compile := Some("org.dsa.iot.benchmark.BrokerConnectionTest")
	
// dependencies
libraryDependencies ++= Seq(
  "org.iot-dsa"        %% "sdk-dslink-scala"        % SCALA_DSA_VERSION,
  "org.scalatest"      %% "scalatest"               % "2.2.1"         % "test",
  "org.scalacheck"     %% "scalacheck"              % "1.12.1"        % "test"  
)
