name := "FunDA"

version := "1.0.0-SNAPSHOT"

organization := "com.bayakala"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "com.h2database" % "h2" % "1.4.191",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.1.1",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "co.fs2" %% "fs2-core" % "0.9.2",
  "co.fs2" %% "fs2-io" % "0.9.2",
  "com.typesafe.play" % "play-iteratees-reactive-streams_2.11" % "2.6.0"
)