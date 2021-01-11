import Dependencies._
import com.typesafe.sbt.packager.docker._

parallelExecution in Test := true

initialize ~= { _ =>
  System.setProperty("config.file", "conf/application.conf")
}

fork := true
connectInput in run := true

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)
//enablePlugins(JavaAppPackaging, AshScriptPlugin)
maintainer := "Dev0 <dev0@syspulse.io>"
dockerBaseImage := "openjdk:8-jre-alpine"
dockerUpdateLatest := true
dockerUsername := Some("syspulse")
dockerExposedVolumes := Seq(s"${appDockerRoot}/logs",s"${appDockerRoot}/conf","/data")
//dockerRepository := "docker.io"
dockerExposedPorts := Seq(8080)

defaultLinuxInstallLocation in Docker := appDockerRoot

mappings in Universal += file("conf/application.conf") -> "conf/application.conf"
mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml"
bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf""""
bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml""""

// bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf""""
// bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""

//s"${(defaultLinuxInstallLocation in Docker).value}/bin/${executableScriptName.value}")
// dockerCommands ++= Seq(
//   ExecCmd("RUN",
//     "mv", 
//      s"${(defaultLinuxInstallLocation in Docker).value}/conf",
//      s"${(defaultLinuxInstallLocation in Docker).value}/${appName}/")
// )

daemonUserUid in Docker := None
daemonUser in Docker := "daemon"

val shared = Seq(
    //retrieveManaged := true,
  
    organization    := "io.syspulse",
    scalaVersion    := "2.13.3",
    name            := "skel",
    version         := appVersion,

    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:existentials", "-language:implicitConversions", "-language:higherKinds", "-language:reflectiveCalls", "-language:postfixOps"),
    javacOptions ++= Seq("-target", "1.8", "-source", "1.8"),
//    manifestSetting,
//    publishSetting,
    resolvers ++= Seq(Opts.resolver.sonatypeSnapshots, Opts.resolver.sonatypeReleases),
    crossVersion := CrossVersion.binary,
    resolvers ++= Seq(
      "spray repo"         at "https://repo.spray.io/",
      "sonatype releases"  at "https://oss.sonatype.org/content/repositories/releases/",
      "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "typesafe repo"      at "https://repo.typesafe.com/typesafe/releases/",
      "confluent repo"     at "https://packages.confluent.io/maven/",
    ),
  )

val sharedAssembly = Seq(
  assemblyMergeStrategy in assembly := {
      case x if x.contains("module-info.class") => MergeStrategy.discard
      case x => {
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
      }
    },

    test in assembly := {}
)

lazy val root = (project in file("."))
  .aggregate(core, http, kafka)
  .dependsOn(core, http, kafka)
  .disablePlugins(sbtassembly.AssemblyPlugin) // this is needed to prevent generating useless assembly and merge error
  .settings(
    
    shared,
  )

lazy val core = (project in file("skel-core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      shared,
      name := "skel-core",
      libraryDependencies ++= libAkka ++ libHttp ++ libCommon ++ libSkel ++ libTest ++ Seq(),
    )

lazy val http= (project in file("skel-http"))
  .dependsOn(core)
  .settings (
    shared,
    sharedAssembly,

    name := appNameHttp,
    libraryDependencies ++= libHttp ++ libDB ++ Seq(
      
    ),
    
    mainClass in run := Some(appBootClassHttp),
    mainClass in assembly := Some(appBootClassHttp),
    assemblyJarName in assembly := jarPrefix + appNameHttp + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val kafka= (project in file("skel-kafka"))
  .dependsOn(core)
  .settings (
    shared,
    sharedAssembly,

    name := appNameKafka,
    libraryDependencies ++= Seq(
      libAkkaKafka,
      libKafkaAvroSer
    ),
    
    mainClass in run := Some(appBootClassKafka),
    mainClass in assembly := Some(appBootClassKafka),
    assemblyJarName in assembly := jarPrefix + appNameKafka + "-" + "assembly" + "-"+ appVersion + ".jar",

  )  
