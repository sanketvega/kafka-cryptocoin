name := "kafka-cryptocoin"
organization := "coinsmith"
scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.2"
libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.4.2"
libraryDependencies += "org.apache.kafka" %% "kafka" % "0.8.2.1"
libraryDependencies += "com.xeiam.xchange" % "xchange-core" % "3.1.0"
libraryDependencies += "com.xeiam.xchange" % "xchange-bitstamp" % "3.1.0"
libraryDependencies += "com.xeiam.xchange" % "xchange-bitfinex" % "3.1.0"
libraryDependencies += "com.xeiam.xchange" % "xchange-okcoin" % "3.1.0"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.3.0"
libraryDependencies += "org.glassfish.tyrus.bundles" % "tyrus-standalone-client-jdk" % "1.12"
libraryDependencies += "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.12"

dependencyOverrides += "io.netty" % "netty-all" % "4.1.0.Beta8"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

enablePlugins(GitVersioning)
enablePlugins(DockerPlugin)

import ReleaseTransformations._
// default release process with publishing, tagging, and pushing removed
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  setNextVersion,
  commitNextVersion
)
