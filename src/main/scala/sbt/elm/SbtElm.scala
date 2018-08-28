package sbt.elm

import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.web.{CompileProblems, LineBasedProblem, SbtWeb}
import sbt.Keys._
import sbt._
import xsbti.{Problem, Severity}

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.util.{Failure, Success, Try}
import scala.sys.process._

object SbtElm extends AutoPlugin {

  object autoImport {

    object ElmKeys {
      /*
        The `make` command compiles Elm code into JS or HTML:

            elm make <zero-or-more-elm-files>

        For example:

            elm make src/Main.elm

        This tries to compile an Elm file named src/Main.elm, putting the resulting
        JavaScript code in an elm.js file.

        You can customize this command with the following flags:

            --debug
                Turn on the time-travelling debugger. It allows you to rewind and replay
                events. The events can be imported/exported into a file, which makes for
                very precise bug reports!

            --optimize
                Turn on optimizations to make code smaller and faster. For example, the
                compiler renames record fields to be as short as possible and unboxes
                values to reduce allocation.

            --output=<output-file>
                Specify the name of the resulting JS file. For example
                --output=assets/elm.js to generate the JS at assets/elm.js or
                --output=/dev/null to generate no output at all!

            --report=<report-type>
                You can say --report=json to get error messages as JSON. This is only
                really useful if you are an editor plugin. Humans should avoid it!

            --docs=<json-file>
                Generate a JSON file of documentation for a package. Eventually it will
                be possible to preview docs with `reactor` because it is quite hard to
                deal with these JSON files directly.
       */
      val elmMake = TaskKey[Seq[File]]("elm-make", "Compile an Elm file or project into JS or HTML.")

      /*
        The `reactor` command starts a local server on your computer:

            elm reactor

        After running that command, you would have a server at <http://localhost:8000>
        that helps with development. It shows your files like a file viewer. If you
        click on an Elm file, it will compile it for you! And you can just press the
        refresh button in the browser to recompile things.

        You can customize this command with the following flags:

            --port=<port>
                The port of the server (default: 8000)
       */
      val elmReactor = TaskKey[Unit]("elm-reactor", "Develop with compile-on-refresh and time-travel debugging for Elm.")

      /*
        The `repl` command opens up an interactive programming session:

            elm repl

        Start working through <https://guide.elm-lang.org> to learn how to use this! It
        has a whole chapter that uses the REPL for everything, so that is probably the
        quickest way to get started.

        You can customize this command with the following flags:

            --interpreter=<interpreter>
                Path to a alternate JS interpreter, like node or nodejs.

            --no-colors
                Turn off the colors in the REPL. This can help if you are having trouble
                reading the values. Some terminals use a custom color scheme that
                diverges significantly from the standard ANSI colors, so another path
                may be to pick a more standard color scheme.
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
    elmExecutable in elmMake := "elm make",
    elmOptions in elmMake := Seq(),
    elmOutput in elmMake := (resourceManaged in elmMake).value / "js" / "elmMain.js",
    includeFilter in elmMake := "*.elm",
    sources in elmMake := ((sourceDirectories in elmMake).value **
      ((includeFilter in elmMake).value -- (excludeFilter in elmMake).value)).get,
    elmMake := {
      val srcs = (sources in elmMake).value

      val hash = OpInputHash.hashString(
        (((elmExecutable in elmMake).value +: (elmOptions in elmMake).value)
          ++ srcs :+ (elmOutput in elmMake).value).mkString("\u0000"))

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

    // Elm Reactor
    elmExecutable in elmReactor := "elm reactor",
    elmOptions in elmReactor := Nil,
    elmReactor := {
      val command = (elmExecutable in elmReactor).value +: (elmOptions in elmReactor).value
      Process(command).run(true).exitValue()
    },

    // Elm REPL
    elmExecutable in elmRepl := "elm repl",
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
      elmReactor := (elmReactor in Assets).value,
      elmRepl := (elmRepl in Assets).value
    )

  def doCompile(command: Seq[String], sourceFiles: Seq[File]): Seq[Problem] = {
    val (buffer, pscLogger) = logger
    val exitStatus = command.mkString(" ") ! pscLogger
    if (exitStatus != 0) PscOutputParser.readProblems(buffer mkString "\n", sourceFiles).get
    else Nil
  }

  def logger = {
    val lineBuffer = new ArrayBuffer[String]
    val logger = new ProcessLogger {
      override def out(s: => String) = lineBuffer += s

      override def err(s: => String) = lineBuffer += s

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
