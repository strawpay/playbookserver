name := "rocannon"

lazy val rocannon = (project in file(".")).
  enablePlugins(BuildInfoPlugin, PlayScala, GitVersioning, GitBranchPrompt).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion
    ))

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

libraryDependencies ++= Seq(
  "org.webjars" % "foundation" % "5.5.2",
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "modernizr" % "2.8.3",
  "org.webjars" % "jquery" % "2.1.4",
  "com.logentries" % "logentries-appender" % "1.1.32"
)

maintainer := "Strawpay AB <info@strawpay.com>"

dockerRepository := Some("strawpay-dockerv2-local.jfrog.io")

dockerBaseImage := "strawpay/ansible-java8:2.2.0.0-1.8.0_111"

dockerExposedPorts := Seq(9000)

dockerExposedVolumes in Docker := Seq("/playbooks")

daemonUser in Docker := "root"

//sourceGenerators in Compile <+= buildInfo

git.useGitDescribe := true

versionFile := {
  val file = target.value / "version.json"
  IO.write(file, s"""{"version":"${version.value}"}""")
  println(version.value)
}

lazy val versionFile = taskKey[Unit]("creates a version file")
