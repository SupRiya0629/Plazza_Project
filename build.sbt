// EV Charging Network - Backend API Server

name := "ev-charging-network"
version := "1.0.0"
scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % "0.23.16",
  "org.http4s" %% "http4s-dsl" % "0.23.16",
  "org.http4s" %% "http4s-circe" % "0.23.16",
  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",
  "org.typelevel" %% "cats-effect" % "3.5.0",
  "org.slf4j" % "slf4j-simple" % "2.0.9",
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked"
)

// Run the server in its own JVM.
Compile / run / fork := true
