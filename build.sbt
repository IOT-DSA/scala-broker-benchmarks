// properties
val APP_VERSION = "0.2.0-SNAPSHOT"
val SCALA_VERSION = "2.12.6"
val AKKA_VERSION = "2.5.13"
val AKKA_HTTP_VERSION = "10.1.3"

// settings
name := "scala-broker-benchmarks"
organization := "org.iot-dsa"
version := APP_VERSION
scalaVersion := SCALA_VERSION

// building
resolvers += Resolver.bintrayRepo("cakesolutions", "maven")
scalacOptions ++= Seq(
  "-feature", 
  "-unchecked", 
  "-deprecation", 
  "-Yno-adapted-args", 
  "-Ywarn-dead-code", 
  "-language:_", 
  "-target:jvm-1.8", 
  "-encoding", "UTF-8",
  "-Xexperimental")

// packaging
enablePlugins(JavaAppPackaging)
mainClass in Compile := Some("org.dsa.iot.benchmark.BrokerConnectionTest")
	
// dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka"       %% "akka-stream"             % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-slf4j"              % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-actor-typed"        % "2.5.11",
  "com.typesafe.akka"       %% "akka-http"               % AKKA_HTTP_VERSION,
  "com.typesafe.play"       %% "play-json"               % "2.6.8",
  "de.heikoseeberger"       %% "akka-http-play-json"     % "1.19.0",
  "org.bouncycastle"         % "bcprov-jdk15on"          % "1.51",
  "com.google.guava"         % "guava"                   % "23.0",
  "ch.qos.logback"           % "logback-classic"         % "1.2.3",
  "org.scalatest"           %% "scalatest"               % "3.0.4"             % "test",
  "org.scalacheck"          %% "scalacheck"              % "1.13.5"            % "test",
  "com.typesafe.akka"       %% "akka-testkit"            % AKKA_VERSION        % "test"
)
