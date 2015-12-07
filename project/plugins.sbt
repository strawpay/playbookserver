logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

// docker plugin
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.5")

// git
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.4")

// Build info
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.2")
