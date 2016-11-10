import com.typesafe.sbt.SbtGit.GitKeys._
import sbtrelease._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _, _}

sbtPlugin := true

organization := "io.finstack"

bintrayOrganization := Some("finstack")

name := "sbt-elm"

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

// Deduce version from Git tags
enablePlugins(BuildInfoPlugin, GitVersioning, GitBranchPrompt)

showCurrentGitBranch

git.useGitDescribe := true
val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r
git.gitTagToVersionNumber := {
  case VersionRegex(v,"") => Some(v)
  case VersionRegex(v,"SNAPSHOT") => Some(s"$v-SNAPSHOT")
  case VersionRegex(v,s) => Some(s"$v-$s-SNAPSHOT")
  case _ => None
}

git.baseVersion := "0.0.0"
git.gitDescribedVersion := gitReader.value.withGit(_.describedVersion).flatMap(v =>
  Option(v).map(_.drop(1)).orElse(formattedShaVersion.value).orElse(Some(git.baseVersion.value))
)

// Settings for SBT Release
def setVersionOnly(selectVersion: sbtrelease.Versions => String): ReleaseStep =  { st: State =>
  val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error(
    "No versions are set! Was this release part executed before inquireVersions?"))
  val selected = selectVersion(vs)

  st.log.info("Setting version to '%s'." format selected)
  val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

  reapply(Seq(
    if (useGlobal) version in ThisBuild := selected
    else version := selected
  ), st)
}

lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

releaseVersion := releaseVersionBump(bumper => {
  (ver: String) => sbtrelease.Version(ver)
    .map(_.withoutQualifier)
    .map(_.bump(bumper).string).getOrElse(versionFormatError)
}).value

// To release run sbt release
releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  runTest,
  tagRelease,
  ReleaseStep(releaseStepTask(publish)),
  pushChanges
)
