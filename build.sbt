name := "FunDA"

version := "1.0.0-SNAPSHOT"

organization := "com.bayakala"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "com.h2database" % "h2" % "1.4.191",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "co.fs2" %% "fs2-core" % "0.9.4",
  "co.fs2" %% "fs2-io" % "0.9.4",
  "com.typesafe.play" % "play-iteratees-reactive-streams_2.11" % "2.6.0"
)
