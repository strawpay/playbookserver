name := "playbookserver"

lazy val playbookserver = (project in file(".")).
  enablePlugins(PlayScala, GitVersioning, GitBranchPrompt)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

maintainer := "Strawpay AB <info@strawpay.com>"

dockerRepository := Some("strawpay.artifactoryonline.com")

dockerBaseImage := "relateiq/oracle-java8"

dockerExposedPorts := Seq(9000)

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  scalaVersion,
  sbtVersion
)

git.useGitDescribe := true

versionFile := {
  val file = target.value / "version.txt"
  IO.write(file, s"${version.value}")
  println(version.value)
}

lazy val versionFile = taskKey[Unit]("creates a version file")
