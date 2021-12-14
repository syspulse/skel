import scala.sys.process.Process
import Dependencies._
import com.typesafe.sbt.packager.docker._

Global / onChangedBuildSource := ReloadOnSourceChanges

Test / parallelExecution := true

initialize ~= { _ =>
  System.setProperty("config.file", "conf/application.conf")
}

fork := true
run / connectInput := true

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
  Docker / publish := Def.sequential(
    Docker / publishLocal,
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

  Docker / defaultLinuxInstallLocation := appDockerRoot,

  Docker / daemonUserUid := None, //Some("1000"), 
  Docker / daemonUser := "daemon"
)

val sharedConfig = Seq(
    //retrieveManaged := true,  
    organization    := "io.syspulse",
    scalaVersion    := "2.13.6",
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
  assembly / assemblyMergeStrategy := {
      case x if x.contains("module-info.class") => MergeStrategy.discard
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.first
      case x => {
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
      }
  },
  assembly / assemblyExcludedJars := {
    val cp = (assembly / fullClasspath).value
    cp filter { f =>
      f.data.getName.contains("snakeyaml-1.27-android.jar") || f.data.getName.contains("jakarta.activation-api-1.2.1") 
      //|| f.data.getName == "spark-core_2.11-2.0.1.jar"
    }
  },
  
  assembly / test := {}
)

lazy val root = (project in file("."))
  .aggregate(core, cron, `skel-test`, http, auth, user, kafka, world, shop, ingest, otp, crypto, flow)
  .dependsOn(core, cron, `skel-test`, http, auth, user, kafka, world, shop, ingest, otp, crypto, flow, scrap, ekm, npp)
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
        Seq(libUUID),
    )

lazy val `skel-test` = (project in file("skel-test"))
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

lazy val cron = (project in file("skel-cron"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      name := "skel-cron",
      libraryDependencies ++= 
        libCommon ++ 
        libTest ++ 
        Seq(
          libQuartz
        ),
      run / mainClass := Some(appBootClassCron),
      assembly / mainClass := Some(appBootClassCron),
      Compile / mainClass := Some(appBootClassCron),
      assembly / assemblyJarName := jarPrefix + appNameCron + "-" + "assembly" + "-"+  appVersion + ".jar",
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

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameHttp,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    run / mainClass := Some(appBootClassHttp),
    assembly / mainClass := Some(appBootClassHttp),
    Compile / mainClass := Some(appBootClassHttp), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameHttp + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val auth = (project in file("skel-auth"))
  .dependsOn(core)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    name := appNameAuth,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    run / mainClass := Some(appBootClassAuth),
    assembly / mainClass := Some(appBootClassAuth),
    assembly / assemblyJarName := jarPrefix + appNameAuth + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val otp = (project in file("skel-otp"))
  .dependsOn(core,`skel-test` % Test)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameOtp,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
        libKuroOtp,
        libQR
    ),
    
    run / mainClass := Some(appBootClassOtp),
    assembly / mainClass := Some(appBootClassOtp),
    Compile / mainClass := Some(appBootClassOtp), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameOtp + "-" + "assembly" + "-"+  appVersion + ".jar",

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

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameUser,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      
    ),
    
    run / mainClass := Some(appBootClassHttp),
    assembly / mainClass := Some(appBootClassHttp),
    Compile / mainClass := Some(appBootClassHttp), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameUser + "-" + "assembly" + "-"+  appVersion + ".jar",

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
    
    run / mainClass := Some(appBootClassKafka),
    assembly / mainClass := Some(appBootClassKafka),
    assembly / assemblyJarName := jarPrefix + appNameKafka + "-" + "assembly" + "-"+ appVersion + ".jar",

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

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameWorld,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCsv
    ),
    
    run / mainClass := Some(appBootClassWorld),
    assembly / mainClass := Some(appBootClassWorld),
    Compile / mainClass := Some(appBootClassWorld), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameWorld + "-" + "assembly" + "-"+  appVersion + ".jar",

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

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameShop,
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCsv,
      libFaker
    ),
    
    run / mainClass := Some(appBootClassShop),
    assembly / mainClass := Some(appBootClassShop),
    Compile / mainClass := Some(appBootClassShop), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameShop + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val ingest = (project in file("skel-ingest"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    name := "skel-ingest",
    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libUpickleLib
    ),
    
    assembly / assemblyJarName := jarPrefix + appNameIngest + "-" + "assembly" + "-"+  appVersion + ".jar",
  )

lazy val crypto = (project in file("skel-crypto"))
  .dependsOn(core)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "skel-crypto",
      libraryDependencies ++= libTest ++ libWeb3j ++ Seq(
        libOsLib,libUpickleLib
      )
    )

lazy val flow = (project in file("skel-flow"))
  .dependsOn(core)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "skel-flow",
      libraryDependencies ++= libTest ++ Seq(
        libOsLib,
        libUpickleLib,
      )
    )


lazy val scrap = (project in file("skel-scrap"))
  .dependsOn(core,cron,flow)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    name := appNameScrap,
    run / mainClass := Some(appBootClassScrap),
    assembly / mainClass := Some(appBootClassScrap),
    Compile / mainClass := Some(appBootClassScrap), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameScrap + "-" + "assembly" + "-"+  appVersion + ".jar",
    
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",
    
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCask,
      libOsLib,
      libUpickleLib,
      libScalaScraper,
      libInfluxDB
    ),
     
  )

lazy val ekm = (project in file("demo/skel-ekm"))
  .dependsOn(core,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",

    name := appNameEkm,
    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libUpickleLib
    ),
    
    run / mainClass := Some(appBootClassEkm),
    assembly / mainClass := Some(appBootClassEkm),
    Compile / mainClass := Some(appBootClassEkm), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameEkm + "-" + "assembly" + "-"+  appVersion + ".jar",

  )

lazy val npp = (project in file("demo/skel-npp"))
  .dependsOn(core,cron,flow,scrap)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    name := appNameNpp,
    run / mainClass := Some(appBootClassNpp),
    assembly / mainClass := Some(appBootClassNpp),
    Compile / mainClass := Some(appBootClassNpp), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameNpp + "-" + "assembly" + "-"+  appVersion + ".jar",
    
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",
    
    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCask,
      libOsLib,
      libUpickleLib,
      libScalaScraper,
      libInfluxDB
    ),  
  )

lazy val twit = (project in file("demo/skel-twit"))
  .dependsOn(core,cron,flow,scrap,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    name := appNameTwit,
    run / mainClass := Some(appBootClassNpp),
    assembly / mainClass := Some(appBootClassNpp),
    Compile / mainClass := Some(appBootClassNpp), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appNameTwit + "-" + "assembly" + "-"+  appVersion + ".jar",
    
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",
    
    libraryDependencies ++= libHttp ++ libTest ++ Seq(
      libTwitter4s,
      libAlpakkaCassandra,
      libSeleniumJava,
      libSeleniumFirefox
    ),  
  )