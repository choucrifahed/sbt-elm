import ElmKeys._

lazy val root = (project in file(".")).enablePlugins(SbtWeb)

val elmCompiled = taskKey[Unit]("Test if the last command was elm-make compiling something")
