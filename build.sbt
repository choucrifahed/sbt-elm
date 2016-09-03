sbtPlugin := true

organization := "io.finstack"

name := "sbt-elm"

version := "0.1.0-SNAPSHOT"

scalacOptions += "-feature"

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.0")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

pomExtra :=
  <scm>
    <url>git@github.com:choucrifahed/sbt-elm.git</url>
    <connection>scm:git:git@github.com:choucrifahed/sbt-elm.git</connection>
  </scm>
    <developers>
      <developer>
        <id>choucrifahed</id>
        <name>Choucri FAHED</name>
        <url>https://github.com/choucrifahed</url>
      </developer>
    </developers>

ScriptedPlugin.scriptedSettings

scriptedLaunchOpts ++= Seq("-Dplugin.version=" + version.value)
