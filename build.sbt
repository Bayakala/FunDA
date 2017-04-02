lazy val commonSettings = Seq(
  organization := "com.bayakala"
  , version := "1.0.1"
  , scalaVersion := "2.11.8"
  , libraryDependencies ++= Seq(
    "com.typesafe.slick" %% "slick" % "3.2.0",
    "com.h2database" % "h2" % "1.4.191",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "co.fs2" %% "fs2-core" % "0.9.4",
    "co.fs2" %% "fs2-io" % "0.9.4",
    "com.typesafe.play" % "play-iteratees-reactive-streams_2.11" % "2.6.0"
  )

)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    sbtPlugin := true,
    name := "FunDA",
    description := "a functional library to supplement FRM like Slick",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    publishMavenStyle := false,
    bintrayRepository := "funda",
    bintrayOrganization in bintray := Some("bayakala"),
    scalacOptions ++= Seq(
      "-feature"
      , "-deprecation"
      , "-language:higherKinds"
    )
  )
