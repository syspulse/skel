//addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
// addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.4")

//addSbtPlugin("com.lightbend.sbt" % "sbt-multi-release-jar" % "0.1.2")

// protobuf generate
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.11"
