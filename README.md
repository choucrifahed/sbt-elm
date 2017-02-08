# sbt-elm

sbt-elm is a Scala Build Tool (SBT) plugin that integrates [Elm](http://elm-lang.org/) projects in Scala/Play projects.
It uses and follows the conventions of [sbt-web](https://github.com/sbt/sbt-web).

## Usage

To use a stable release, add the following to the `project/plugins.sbt` of your project:

    addSbtPlugin("io.finstack" % "sbt-elm" % "0.1.1")

To use the latest from Github, add the following to the `project/plugins.sbt` of your project:

    lazy val root = project.in(file(".")).dependsOn(sbtElm)
    lazy val sbtElm = uri("https://github.com/choucrifahed/sbt-elm.git")

Then:

  * Put your Elm files (with extension `elm` into `src/main/assets/elm`)
  * Run the `elm-make` task
  * Result is in `target/web/public/main/js`

Or in a Play Framework project:

  * Put your Elm files (with extension `elm` into `app/assets/elm`)
  * Run Play
  * Observe the JS file on `http://localhost:9000/assets/elmMain.js`

There are other tasks available:

 * `elm-reactor` task allows you to run [Elm Reactor](https://github.com/elm-lang/elm-reactor) from SBT.
 * `elm-repl` task will run the Elm interpreter, with your sources loaded.

However, [Elm Package](https://github.com/elm-lang/elm-package) is not yet available from SBT to install packages, bump versions or publish your packages.

## Multimodule Play project

Unfortunately, the current version of the plugin does not support multiple Play sub-modules. Therefore you can only have SbtElm enabled for a single module (see #2 for more details). The example below shows how to do this:

```
// in project/plugins.sbt: addSbtPlugin("io.finstack" % "sbt-elm" % "x.y.z")

// then in build.sbt

lazy val playModuleElm = project
  .in(file("playModuleElm"))
  .enablePlugins(PlayScala)
  ...

lazy val playModule2 = project
  .in(file("playModule2"))
  .enablePlugins(PlayScala)
  .disablePlugins(SbtElm)
  ...
```

## Examples

Check the examples folder for example projects that use this plugin.

## Adding arguments to elm-make

This setting for example enables the debugger (Elm 0.18+)

```
(ElmKeys.elmOptions in ElmKeys.elmMake in Assets) ++= Seq("--debug")
```

## Hacking on the plugin

Run `scripted` to run the plugin test suite (which is barely existent at this point...)

## Special Thanks

To the team behind [sbt-purescript](https://github.com/eamelink/sbt-purescript) for heavily inspiring this plugin.
