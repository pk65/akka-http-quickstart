lazy val akkaHttpVersion = "10.2.10"
lazy val akkaVersion = "2.6.20"
lazy val tapirVersion = "1.1.0"
// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "priv.home",
      scalaVersion := "2.13.8"
    )),
    name := "akka-http-quickstart",
    scalacOptions ++= Seq("-Xlog-implicits"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion withJavadoc(),
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion withJavadoc(),
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion withJavadoc(),
      "com.typesafe.akka" %% "akka-stream" % akkaVersion withJavadoc(),
      "ch.qos.logback" % "logback-classic" % "1.2.11", // keep it for slf4j-api version
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion withJavadoc(),
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion withJavadoc(),
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % tapirVersion withJavadoc(),
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion withJavadoc(),
      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "1.1.0" exclude("com.typesafe.akka", "akka-stream_2.13") withJavadoc(),
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.12" % Test
    )
  )
