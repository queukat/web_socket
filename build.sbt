name := "WebSocketOrderedMessages"

version := "0.1"

scalaVersion := "2.13.11"

libraryDependencies ++= Seq(
  "org.java-websocket" % "Java-WebSocket" % "1.5.2",
  "com.typesafe.play" %% "play-json" % "2.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.6",
  "org.apache.logging.log4j" % "log4j-api" % "2.14.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.14.1",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.14.1"
)

lazy val root = (project in file("."))
  .settings(
    name := "test_Skanestas"
  )
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.defaultMergeStrategy(x)
}
