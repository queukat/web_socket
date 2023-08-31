name := "WebSocketOrderedMessages"

version := "0.1"

scalaVersion := "2.13.11"

// Add necessary dependencies
libraryDependencies ++= Seq(
  "org.java-websocket" % "Java-WebSocket" % "1.5.2", // For WebSocket communication
  "com.typesafe.play" %% "play-json" % "2.9.2",       // For JSON parsing
  "ch.qos.logback" % "logback-classic" % "1.2.6",
  "org.apache.logging.log4j" % "log4j-api" % "2.14.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.14.1",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.14.1" // если вы хотите использовать SLF4J API

)


// If you need additional resolvers, add them here. For example:
// resolvers += "Some Resolver" at "https://some-resolver-url.com"
lazy val root = (project in file("."))
  .settings(
    name := "test_Skanestas"
  )
