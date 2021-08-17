import scala.sys.process.Process
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

// Huge Credits -> https://softwaremill.com/how-to-build-multi-platform-docker-image-with-sbt-and-docker-buildx
lazy val ensureDockerBuildx = taskKey[Unit]("Ensure that docker buildx configuration exists")
lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")
lazy val dockerBuildxSettings = Seq(
  ensureDockerBuildx := {
    if (Process("docker buildx inspect multi-arch-builder").! == 1) {
      Process("docker buildx create --use --name multi-arch-builder", baseDirectory.value).!
    }
  },
  dockerBuildWithBuildx := {
    streams.value.log("Building and pushing image with Buildx")
    dockerAliases.value.foreach(
      alias => Process("docker buildx build --platform=linux/arm64,linux/amd64 --push -t " +
        alias + " .", baseDirectory.value / "target" / "docker"/ "stage").!
    )
  },
  publish in Docker := Def.sequential(
    publishLocal in Docker,
    ensureDockerBuildx,
    dockerBuildWithBuildx
  ).value
)

val sharedConfigDocker = Seq(
  maintainer := "Dev0 <dev0@syspulse.io>",
  // openjdk:8-jre-alpine - NOT WORKING ON RP4+ (arm64). Crashes JVM in kubernetes
  dockerBaseImage := "openjdk:18-slim", //"openjdk:8u212-jre-alpine3.9", //"openjdk:8-jre-alpine",
  dockerUpdateLatest := true,
  dockerUsername := Some("syspulse"),
  dockerExposedVolumes := Seq(s"${appDockerRoot}/logs",s"${appDockerRoot}/conf",s"${appDockerRoot}/data","/data"),
  //dockerRepository := "docker.io",
  dockerExposedPorts := Seq(8080),

  defaultLinuxInstallLocation in Docker := appDockerRoot,

  daemonUserUid in Docker := None, //Some("1000"), 
  daemonUser in Docker := "daemon"
)

val sharedConfig = Seq(
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

// assemblyMergeStrategy in assembly := {
  // case "application.conf" => MergeStrategy.concat
  // case "reference.conf" => MergeStrategy.concat
  // case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  // case PathList("META-INF/MANIFEST.MF", xs @ _*) => MergeStrategy.discard
  // case PathList("snakeyaml-1.27-android.jar", xs @ _*) => MergeStrategy.discard
  // case PathList("commons-logging-1.2.jar", xs @ _*) => MergeStrategy.discard
  // case x => MergeStrategy.first
// }


val sharedConfigAssembly = Seq(
  assemblyMergeStrategy in assembly := {
      case x if x.contains("module-info.class") => MergeStrategy.discard
      case x => {
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
      }
  },
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    cp filter { f =>
      f.data.getName.contains("snakeyaml-1.27-android.jar") 
      //||
      //f.data.getName == "spark-core_2.11-2.0.1.jar"
    }
  },
  
  test in assembly := {}
)

lazy val root = (project in file("."))
  .aggregate(core, skel_test, http, auth, user, kafka, world, shop, ingest, otp)
  .dependsOn(core, skel_test, http, auth, user, kafka, world, shop, ingest, otp)
  .disablePlugins(sbtassembly.AssemblyPlugin) // this is needed to prevent generating useless assembly and merge error
  .settings(
    
    sharedConfig,
    sharedConfigDocker,
    dockerBuildxSettings
  )

lazy val core = (project in file("skel-core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "skel-core",
      libraryDependencies ++= 
        libAkka ++ 
        libHttp ++ 
        libCommon ++ 
        libSkel ++ 
        libDB ++ 
        libTest ++ 
        Seq(),
    )

lazy val skel_test = (project in file("skel-test"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "skel-test",
      libraryDependencies ++= 
        libAkka ++ 
        libHttp ++ 
        libCommon ++ 
        libSkel ++ 
        libDB ++ 
        libTestLib ++
        Seq(),
    )


lazy val http = (project in file("skel-http"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    sharedConfigDocker,
    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameHttp,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    mainClass in run := Some(appBootClassHttp),
    mainClass in assembly := Some(appBootClassHttp),
    assemblyJarName in assembly := jarPrefix + appNameHttp + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val auth = (project in file("skel-auth"))
  .dependsOn(core)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    name := appNameAuth,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    mainClass in run := Some(appBootClassAuth),
    mainClass in assembly := Some(appBootClassAuth),
    assemblyJarName in assembly := jarPrefix + appNameAuth + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val otp = (project in file("skel-otp"))
  .dependsOn(core,skel_test % Test)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameOtp,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
        libKuroOtp,
        libQR
    ),
    
    mainClass in run := Some(appBootClassOtp),
    mainClass in assembly := Some(appBootClassOtp),
    assemblyJarName in assembly := jarPrefix + appNameOtp + "-" + "assembly" + "-"+  appVersion + ".jar",

  )


lazy val user = (project in file("skel-user"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameUser,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    mainClass in run := Some(appBootClassHttp),
    mainClass in assembly := Some(appBootClassHttp),
    assemblyJarName in assembly := jarPrefix + appNameUser + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val kafka= (project in file("skel-kafka"))
  .dependsOn(core)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    name := appNameKafka,
    libraryDependencies ++= Seq(
      libAkkaKafka,
      libKafkaAvroSer
    ),
    
    mainClass in run := Some(appBootClassKafka),
    mainClass in assembly := Some(appBootClassKafka),
    assemblyJarName in assembly := jarPrefix + appNameKafka + "-" + "assembly" + "-"+ appVersion + ".jar",

  )  

lazy val world = (project in file("skel-world"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameWorld,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCsv
    ),
    
    mainClass in run := Some(appBootClassWorld),
    mainClass in assembly := Some(appBootClassWorld),
    assemblyJarName in assembly := jarPrefix + appNameWorld + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val shop = (project in file("skel-shop"))
  .dependsOn(core,world)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameShop,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCsv,
      libFaker
    ),
    
    mainClass in run := Some(appBootClassShop),
    mainClass in assembly := Some(appBootClassShop),
    assemblyJarName in assembly := jarPrefix + appNameShop + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val ingest = (project in file("skel-ingest"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    name := "skel-ingest",
    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libUjsonLib
    ),
    
    assemblyJarName in assembly := jarPrefix + appNameIngest + "-" + "assembly" + "-"+  appVersion + ".jar",
  )

lazy val ekm = (project in file("skel-ekm"))
  .dependsOn(core,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    mappings in Universal += file("conf/application.conf") -> "conf/application.conf",
    mappings in Universal += file("conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameEkm,
    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libUjsonLib
    ),
    
    mainClass in run := Some(appBootClassEkm),
    mainClass in assembly := Some(appBootClassEkm),
    assemblyJarName in assembly := jarPrefix + appNameEkm + "-" + "assembly" + "-"+  appVersion + ".jar",

  )
