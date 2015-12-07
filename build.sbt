name := "rocannon"

lazy val rocannon = (project in file(".")).
  enablePlugins(PlayScala, GitVersioning, GitBranchPrompt)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

libraryDependencies ++= Seq(
  "org.webjars" % "foundation" % "5.5.2",
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "modernizr" % "2.8.3",
  "org.webjars" % "jquery" % "2.1.4")

maintainer := "Strawpay AB <info@strawpay.com>"

dockerRepository := Some("strawpay-docker-dockerv2-local.artifactoryonline.com")

dockerBaseImage := "strawpay/ansible-java8"

dockerExposedPorts := Seq(9000)

dockerExposedVolumes in Docker := Seq("/playbooks")

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
  val file = target.value / "version.json"
  IO.write(file, s"""{"version":"${version.value}"}""")
  println(version.value)
}

lazy val versionFile = taskKey[Unit]("creates a version file")
