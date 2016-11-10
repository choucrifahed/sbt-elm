package sbt.elm

import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.web.{CompileProblems, LineBasedProblem, SbtWeb}
import sbt.Keys._
import sbt._
import xsbti.{Problem, Severity}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}


object SbtElm extends AutoPlugin {

  object autoImport {

    object ElmKeys {
      /*
        Usage: elm-make [FILES...] [--output FILE] [--yes] [--report FORMAT] [--warn]
                        [--docs FILE] [--prepublish] [--prepublish-core]
          build Elm projects

        Available options:
          -h,--help                Show this help text
          --output FILE            Write result to the given .html or .js FILE.
          --yes                    Reply 'yes' to all automated prompts.
          --report FORMAT          Format of error and warning reports (e.g.
                                   --report=json)
          --warn                   Report warnings to improve code quality.
          --docs FILE              Write documentation to FILE as JSON.

        Examples:
          elm-make Main.elm                     # compile to HTML in index.html
          elm-make Main.elm --output main.html  # compile to HTML in main.html
          elm-make Main.elm --output elm.js     # compile to JS in elm.js
          elm-make Main.elm --warn              # compile and report warnings

        Full guide to using elm-make at <https://github.com/elm-lang/elm-make>
       */
      val elmMake = TaskKey[Seq[File]]("elm-make", "Compile an Elm file or project into JS or HTML.")

      /*
        Usage: elm-package COMMAND
          install and publish elm packages

        Available commands:
          install                  Install packages to use locally
          publish                  Publish your package to the central catalog
          bump                     Bump version numbers based on API changes
          diff                     Get differences between two APIs

        To learn more about a particular command run:
          elm-package COMMAND --help
       */
      // FIXME this task is useless if one cannot pass parameters
      // val elmPackage = InputKey[Unit]("elm-package", "Manage Elm packages from <http://package.elm-lang.org>.")

      /*
        Interactive development tool that makes it easy to develop and debug Elm
        programs.
            Read more about it at <https://github.com/elm-lang/elm-reactor>.

        Common flags:
        -a --address=ADDRESS  set the address of the server (e.g. look into 0.0.0.0
                              if you want to try stuff on your phone)
        -p --port=INT         set the port of the reactor (default: 8000)
        -h --help             Display help message
        -v --version          Print version information
           --numeric-version  Print just the version number
       */
      val elmReactor = TaskKey[Unit]("elm-reactor", "Develop with compile-on-refresh and time-travel debugging for Elm.")

      /*
        Read-eval-print-loop (REPL) for digging deep into Elm projects.
        More info at <https://github.com/elm-lang/elm-repl#elm-repl>

        Common flags:
        -c --compiler=FILE     Provide a path to a specific version of elm-make.
        -i --interpreter=FILE  Provide a path to a specific JavaScript interpreter
                               (e.g. node, nodejs, ...).
        -h --help              Display help message
        -v --version           Print version information
           --numeric-version   Print just the version number
       */
      val elmRepl = TaskKey[Unit]("elm-repl", "Elm REPL for running individual expressions.")

      val elmExecutable = settingKey[String]("The Elm executable.")
      val elmOutput = settingKey[File]("Elm output file.")
      val elmOptions = settingKey[Seq[String]]("Elm executable options.")
    }

  }

  override def requires = SbtWeb

  override def trigger = AllRequirements

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.ElmKeys._

  val baseElmSettings = Seq(
    // Elm Make
    elmExecutable in elmMake := "elm-make",
    elmOptions in elmMake := Seq("--warn", "--yes"),
    elmOutput in elmMake := (resourceManaged in elmMake).value / "js" / "elmMain.js",
    includeFilter in elmMake := "*.elm",
    sources in elmMake := ((sourceDirectories in elmMake).value **
      ((includeFilter in elmMake).value -- (excludeFilter in elmMake).value)).get,
    elmMake := {
      val srcs = (sources in elmMake).value

      val hash = OpInputHash.hashString(
        (((elmExecutable in elmMake).value +: (elmOptions in elmMake).value)
          ++ srcs :+ (elmOutput in elmMake).value).mkString("\0"))

      implicit val opInputHasher = OpInputHasher[Unit](_ => hash)

      val (outs, ()) = syncIncremental(streams.value.cacheDirectory / "run", Seq(())) {
        case Seq() => (Map.empty, ())
        case _ =>
          streams.value.log.info(s"Elm compiling on ${srcs.length} source(s)")

          val command = ((elmExecutable in elmMake).value +: (elmOptions in elmMake).value) ++
            ("--output" :: (elmOutput in elmMake).value.absolutePath :: srcs.getPaths.toList)

          val problems = doCompile(command, srcs)
          CompileProblems.report((reporter in elmMake).value, problems)

          (Map(() ->
            (if (problems.nonEmpty) OpFailure
            else OpSuccess(srcs.toSet, Set((elmOutput in elmMake).value)))), ())
      }

      outs.toSeq
    },

    // Elm Package
    //elmExecutable in elmPackage := "elm-package",
    //elmOptions in elmPackage := Nil,
    // FIXME no clue of how input can be read!!!
    /*elmPackage := {
      val args = Def.spaceDelimited("<args>").parsed
      val command = ((elmExecutable in elmPackage).value +: (elmOptions in elmPackage).value) ++ args
      Process(command).run(true).exitValue()
    },*/

    // Elm Reactor
    elmExecutable in elmReactor := "elm-reactor",
    elmOptions in elmReactor := Nil,
    elmReactor := {
      val command = (elmExecutable in elmReactor).value +: (elmOptions in elmReactor).value
      Process(command).run(true).exitValue()
    },

    // Elm REPL
    elmExecutable in elmRepl := "elm-repl",
    elmOptions in elmRepl := Nil,
    elmRepl := {
      val command = (elmExecutable in elmRepl).value +: (elmOptions in elmRepl).value
      Process(command).run(true).exitValue()
    },

    resourceGenerators += elmMake.taskValue)

  override def projectSettings =
    inConfig(Assets)(
      baseElmSettings ++ Seq(
        resourceManaged in elmMake := webTarget.value / "elm" / "main"
      )
    ) ++
    // FIXME include elm-test in test command
    /* inConfig(TestAssets)(
      baseElmSettings ++ Seq(
        resourceManaged in elmMake := webTarget.value / "elm" / "test"
      )
    ) ++ */ Seq(
      elmMake := (elmMake in Assets).value,
      // FIXME elmPackage := (elmPackage in Assets).value,
      elmReactor := (elmReactor in Assets).value,
      elmRepl := (elmRepl in Assets).value
    )

  def doCompile(command: Seq[String], sourceFiles: Seq[File]): Seq[Problem] = {
    val (buffer, pscLogger) = logger
    val exitStatus = command ! pscLogger
    if (exitStatus != 0) PscOutputParser.readProblems(buffer mkString "\n", sourceFiles).get
    else Nil
  }

  def logger = {
    val lineBuffer = new ArrayBuffer[String]
    val logger = new ProcessLogger {
      override def info(s: => String) = lineBuffer += s

      override def error(s: => String) = lineBuffer += s

      override def buffer[T](f: => T): T = f
    }
    (lineBuffer, logger)
  }

  object PscOutputParser {
    val TypeError = """(?s)Error at (.*) line ([0-9]+), column ([0-9]+):\s*\n(.*)""".r
    val ParseError = """(?s)"([^"]+)" \(line ([0-9]+), column ([0-9]+)\):\s*\n(.*)""".r

    def readProblems(pscOutput: String, sourceFiles: Seq[File]): Try[Seq[Problem]] = pscOutput match {
      case TypeError(filePath, lineString, columnString, message) =>
        Success(Seq(problem(filePath, lineString, columnString, message)))
      case ParseError(filePath, lineString, columnString, message) =>
        Success(Seq(problem(filePath, lineString, columnString, message)))
      case other =>
        Failure(new RuntimeException(s"Failed to parse `elm` output. This is the original `elm` output:\n" + pscOutput))
    }

    def problem(filePath: String, lineString: String, columnString: String, message: String) = {
      val file = new File(filePath)
      val line = lineString.toInt
      val column = columnString.toInt - 1
      new LineBasedProblem(message, Severity.Error, line, column, IO.readLines(file).drop(line - 1).headOption.getOrElse(""), file)
    }
  }

}
