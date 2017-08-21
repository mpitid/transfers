
import AssemblyKeys._

name := "transfers"

organization in ThisBuild := "money"

scalacOptions in ThisBuild := Seq("-deprecation", "-feature", "-unchecked")

scalaVersion := "2.12.2"

val http4sVersion = "0.15.14a"

val circeVersion = "0.6.1"

libraryDependencies ++= Seq(
  "io.jvm.uuid" %% "scala-uuid" % "0.2.3" withSources()
, "org.http4s" %% "http4s-dsl" % http4sVersion
, "org.http4s" %% "http4s-blaze-server" % http4sVersion
, "org.http4s" %% "http4s-blaze-client" % http4sVersion
, "org.http4s" %% "http4s-circe" % http4sVersion
, "io.circe" %% "circe-generic" % circeVersion
, "io.circe" %% "circe-literal" % circeVersion
, "io.circe" %% "circe-generic-extras" % circeVersion
, "io.circe" %% "circe-java8" % circeVersion
, "org.log4s" %% "log4s" % "1.3.5"
, "ch.qos.logback" % "logback-classic" % "1.2.3"
, "org.scalatest" %% "scalatest" % "3.0.1" % "test" withSources()
, "org.scalacheck" %% "scalacheck" % "1.13.4" % "test" withSources()
)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

assemblySettings

