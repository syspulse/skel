import scala.sys.process.Process
import Dependencies._
import com.typesafe.sbt.packager.docker.DockerAlias
import com.typesafe.sbt.packager.docker._

Global / onChangedBuildSource := ReloadOnSourceChanges

// https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html#Built-in+Tags+and+Rules
Test / parallelExecution := true
//test / parallelExecution := false
// I am sorry sbt, this is stupid ->
// Non-concurrent execution is needed for Server with starting / stopping HttpServer
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

licenses := Seq(("ASF2", url("https://www.apache.org/licenses/LICENSE-2.0")))

initialize ~= { _ =>
  System.setProperty("config.file", "conf/application.conf")
}

//fork := true
test / fork := true
run / fork := true
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

val dockerRegistryLocal = Seq(
  dockerRepository := Some("docker.u132.net:5000"),
  dockerUsername := Some("syspulse"),
  // this fixes stupid idea of adding registry in publishLocal 
  dockerAlias := DockerAlias(registryHost=None,username = dockerUsername.value, name = name.value, tag = Some(version.value))
)

val dockerRegistryDockerHub = Seq(
  dockerUsername := Some("syspulse")
)

val sharedConfigDocker = Seq(
  maintainer := "Dev0 <dev0@syspulse.io>",
  // openjdk:8-jre-alpine - NOT WORKING ON RP4+ (arm64). Crashes JVM in kubernetes
  // dockerBaseImage := "openjdk:8u212-jre-alpine3.9", //"openjdk:8-jre-alpine",

  //dockerBaseImage := "openjdk:8-jre-alpine",
  dockerBaseImage := "openjdk:18-slim",
  
  dockerUpdateLatest := true,
  dockerUsername := Some("syspulse"),
  dockerExposedVolumes := Seq(s"${appDockerRoot}/logs",s"${appDockerRoot}/conf",s"${appDockerRoot}/data","/data"),
  //dockerRepository := "docker.io",
  dockerExposedPorts := Seq(8080),

  Docker / defaultLinuxInstallLocation := appDockerRoot,

  Docker / daemonUserUid := None, //Some("1000"), 
  Docker / daemonUser := "daemon"
) ++ dockerRegistryLocal

// Spark is not working with openjdk:18-slim (cannot access class sun.nio.ch.DirectBuffer)
// openjdk:8-jre
// Also, Spark has problems with /tmp (java.io.IOException: Failed to create a temp directory (under /tmp) after 10 attempts!)
val sharedConfigDockerSpark = sharedConfigDocker ++ Seq(
  //dockerBaseImage := "openjdk:8-jre-alpine",
  dockerBaseImage := "openjdk:11-jre-slim",
  Docker / daemonUser := "root"
)

val sharedConfig = Seq(
    //retrieveManaged := true,  
    organization    := "io.syspulse",
    scalaVersion    := "2.13.6",
    name            := "skel",
    version         := appVersion,

    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:existentials", "-language:implicitConversions", "-language:higherKinds", "-language:reflectiveCalls", "-language:postfixOps"),
    javacOptions ++= Seq("-target", "1.8", "-source", "1.8"),
    
    crossVersion := CrossVersion.binary,
    resolvers ++= Seq(
      Opts.resolver.sonatypeSnapshots, 
      Opts.resolver.sonatypeReleases,
      "spray repo"         at "https://repo.spray.io/",
      "sonatype releases"  at "https://oss.sonatype.org/content/repositories/releases/",
      "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "typesafe repo"      at "https://repo.typesafe.com/typesafe/releases/",
      "confluent repo"     at "https://packages.confluent.io/maven/",
      "consensys repo"     at "https://artifacts.consensys.net/public/maven/maven/",
      "consensys teku"     at "https://artifacts.consensys.net/public/teku/maven/"
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


val sharedConfigAssemblyTeku = Seq(
  assembly / assemblyMergeStrategy := {
      case x if x.contains("module-info.class") => MergeStrategy.concat
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMarkerBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMDCBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticLoggerBinder.class") => MergeStrategy.first
      case x if x.contains("google/protobuf") => MergeStrategy.first
      case x => {
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
      }
  },
  assembly / assemblyExcludedJars := {
    val cp = (assembly / fullClasspath).value
    cp filter { f => {
        f.data.getName.contains("bcprov-") || 
        f.data.getName.contains("snakeyaml-1.27-android.jar") || 
        f.data.getName.contains("jakarta.activation-api-1.2.1")
      }
    }
  },
  
  assembly / test := {}
)

val sharedConfigAssembly = Seq(
  assembly / assemblyMergeStrategy := {
      case x if x.contains("module-info.class") => MergeStrategy.discard
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMarkerBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMDCBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticLoggerBinder.class") => MergeStrategy.first
      case x if x.contains("google/protobuf") => MergeStrategy.first
      case x => {
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
      }
  },
  assembly / assemblyExcludedJars := {
    val cp = (assembly / fullClasspath).value
    cp filter { f =>
      f.data.getName.contains("snakeyaml-1.27-android.jar") || 
      f.data.getName.contains("jakarta.activation-api-1.2.1") ||
      f.data.getName.contains("jakarta.activation-2.0.1") 
      //|| f.data.getName.contains("activation-1.1.1.jar") 
      //|| f.data.getName == "spark-core_2.11-2.0.1.jar"
    }
  },
  
  assembly / test := {}
)

val sharedConfigAssemblySpark = Seq(
  assembly / assemblyMergeStrategy := {
      case x if x.contains("module-info.class") => MergeStrategy.discard
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMarkerBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticMDCBinder.class") => MergeStrategy.first
      case x if x.contains("slf4j/impl/StaticLoggerBinder.class") => MergeStrategy.first
      case x if x.contains("google/protobuf") => MergeStrategy.first
      case x if x.contains("org/apache/spark/unused/UnusedStubClass.class") => MergeStrategy.first
      case x if x.contains("git.properties") => MergeStrategy.discard
      case x if x.contains("mozilla/public-suffix-list.txt") => MergeStrategy.first
      case x => {
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
      }
  },
  assembly / assemblyExcludedJars := {
    val cp = (assembly / fullClasspath).value
    cp filter { f =>
      f.data.getName.contains("snakeyaml-1.27-android.jar") || 
      f.data.getName.contains("jakarta.activation-api-1.2.1") ||
      f.data.getName.contains("jakarta.activation-api-1.1.1") ||
      f.data.getName.contains("jakarta.activation-2.0.1.jar") ||
      f.data.getName.contains("jakarta.annotation-api-1.3.5.jar") ||
      f.data.getName.contains("jakarta.ws.rs-api-2.1.6.jar") ||
      f.data.getName.contains("commons-logging-1.1.3.ja") ||
      f.data.getName.contains("aws-java-sdk-bundle-1.11.563.jar") ||
      f.data.getName.contains("jcl-over-slf4j-1.7.30.jar") ||
      (f.data.getName.contains("netty") && (f.data.getName.contains("4.1.50.Final.jar") || (f.data.getName.contains("netty-all-4.1.68.Final.jar"))))

      //|| f.data.getName == "spark-core_2.11-2.0.1.jar"
    }
  },
  
  assembly / test := {}
)

def appDockerConfig(appName:String,appMainClass:String) = 
  Seq(
    name := appName,

    run / mainClass := Some(appMainClass),
    assembly / mainClass := Some(appMainClass),
    Compile / mainClass := Some(appMainClass), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appName + "-" + "assembly" + "-"+  appVersion + ".jar",

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",   
  )

def appAssemblyConfig(appName:String,appMainClass:String) = 
  Seq(
    name := appName,
    run / mainClass := Some(appMainClass),
    assembly / mainClass := Some(appMainClass),
    Compile / mainClass := Some(appMainClass),
    assembly / assemblyJarName := jarPrefix + appName + "-" + "assembly" + "-"+  appVersion + ".jar",
  )


// ======================================================================================================================
lazy val root = (project in file("."))
  .aggregate(core, serde, cron, video, skel_test, http, auth_core, auth, user, kafka, ingest, otp, crypto, flow, dsl)
  .dependsOn(core, serde, cron, video, skel_test, http, auth_core, auth, user, kafka, ingest, otp, crypto, flow, dsl, scrap, enroll,yell,skel_notify)
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
        Seq(
          libUUID, 
          libScodecBits
        ),
    )

lazy val serde = (project in file("skel-serde"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "skel-serde",
      libraryDependencies ++= libTest ++ 
        Seq(
          libUUID, 
          libAvro4s,
          libUpickleLib,
          libScodecBits
        ),
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

lazy val cron = (project in file("skel-cron"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      
      appAssemblyConfig("skel-cron",appBootClassCron),
      
      libraryDependencies ++= libCommon ++ libTest ++ 
        Seq(
          libQuartz
        ),
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

    appDockerConfig(appNameHttp,appBootClassHttp),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
    ),
  )

lazy val auth_core = (project in file("skel-auth/auth-core"))
  .dependsOn(core)
  .settings (
    sharedConfig,
    name := "skel-auth-core",
    
    libraryDependencies ++= libJwt ++ Seq(
      libUpickleLib,
      libCasbin
    ),    
  )

lazy val auth = (project in file("skel-auth"))
  .dependsOn(core,crypto,auth_core,user)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    appAssemblyConfig(appNameAuth,appBootClassAuth),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ libJwt ++ Seq(
      libUpickleLib,
      libRequests,
      libScalaTags,
      libCask,
      libCasbin
    ),    
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

    appDockerConfig(appNameOtp,appBootClassOtp),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
        libKuroOtp,
        libQR
    ),
  )


lazy val user = (project in file("skel-user"))
  .dependsOn(core,auth_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig(appNameUser,appBootClassUser),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(  
    ),    
  )

lazy val kafka= (project in file("skel-kafka"))
  .dependsOn(core)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    appAssemblyConfig(appNameKafka,appBootClassKafka),

    libraryDependencies ++= Seq(
      libAkkaKafka,
      libKafkaAvroSer
    ),
  )  

lazy val crypto = (project in file("skel-crypto"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssemblyTeku,
      //sharedConfigAssembly,
      name := "skel-crypto",
      libraryDependencies ++= Seq() ++ //Seq(libLog4j2Api, libLog4j2Core) ++ 
        libTest ++ libWeb3j ++ Seq(
          libOsLib,
          libUpickleLib,
          libScodecBits,
          libHKDF,
          libBLS,
          libBLSKeystore,
          libSSSS,
          libEthAbi,
        ),
      // this is important option to support latest log4j2 
      assembly / packageOptions += sbt.Package.ManifestAttributes("Multi-Release" -> "true")
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

    appDockerConfig(appNameScrap,appBootClassScrap),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libCask,
      libOsLib,
      libUpickleLib,
      libScalaScraper,
      libInfluxDB
    ),
     
  )

lazy val ingest = (project in file("skel-ingest"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    appAssemblyConfig("skel-ingest",""),
    //assembly / assemblyJarName := jarPrefix + appNameIngest + "-" + "assembly" + "-"+  appVersion + ".jar",

    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libScalaTest % Test,
      libAlpakkaFile,
      libUpickleLib,      
    ),        
  )

lazy val ingest_dynamo = (project in file("skel-ingest/ingest-dynamo"))
  .dependsOn(core,video,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig(appNameDynamo,appBootClassDynamo),

    libraryDependencies ++= libHttp ++ libTest ++ Seq(
      libAlpakkaDynamo
    ),  
  )
 
lazy val ingest_elastic = (project in file("skel-ingest/ingest-elastic"))
  .dependsOn(core,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig(appNameElastic,appBootClassElastic),

    libraryDependencies ++= libHttp ++ libTest ++ Seq(
      libAlpakkaElastic
    ),  
  )

lazy val ingest_flow = (project in file("skel-ingest/ingest-flow"))
  .dependsOn(core,ingest,ingest_elastic,kafka)
  .enablePlugins(JavaAppPackaging)
  .settings (
    sharedConfig,
    sharedConfigAssembly,

    appAssemblyConfig("ingest-flow","io.syspulse.skel.ingest.flow.App"),
    //assembly / assemblyJarName := jarPrefix + appNameIngest + "-" + "assembly" + "-"+  appVersion + ".jar",

    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libScalaTest % Test,
      libAlpakkaFile,
      libUpickleLib,      
    ),        
  )

lazy val stream_std = (project in file("skel-stream/stream-std"))
  .dependsOn(core,dsl)
  .enablePlugins(JavaAppPackaging)
  //.enablePlugins(DockerPlugin)
  //.enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    //sharedConfigDocker,
    //dockerBuildxSettings,

    appDockerConfig("stream-std","io.syspulse.skel.stream.AppStream"),

    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ Seq(
      libUpickleLib
    ),

  )

lazy val cli = (project in file("skel-cli"))
  .dependsOn(core,crypto)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    
    appAssemblyConfig("skel-cli","io.syspulse.skel.cli.App"),
    
    libraryDependencies ++= libCommon ++ libHttp ++ libTest ++ 
      Seq(        
        libOsLib,
        libUpickleLib,
        libJline
      ),
  )

lazy val db_cli = (project in file("skel-db/db-cli"))
  .dependsOn(core,cli)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      
      appAssemblyConfig("db-cli","io.syspulse.skel.db.AppCliDB"),
      
      libraryDependencies ++= libCommon ++ libHttp ++ libTest ++ 
        Seq(
          
        ),
    )

lazy val dsl = (project in file("skel-dsl"))
  .dependsOn(core)
  .settings (
      sharedConfig,
      name := "skel-dsl",
      libraryDependencies ++= libCommon ++
        Seq(),
    )


lazy val spark_convert = (project in file("skel-spark/spark-convert"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssemblySpark,
    sharedConfigDockerSpark,
    dockerBuildxSettings,

    appDockerConfig("spark-convert","io.syspulse.skel.spark.CsvConvert"),

    version := "0.0.7",

    libraryDependencies ++= libSparkAWS ++ Seq(
      
    ),
  )

lazy val enroll = (project in file("skel-enroll"))
  .dependsOn(core,crypto,user,skel_notify,skel_test % Test)
  .enablePlugins(JavaAppPackaging)
  //.enablePlugins(DockerPlugin)
  //.enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    //sharedConfigDocker,
    //dockerBuildxSettings,

    name := "skel-enroll",
    // appDockerConfig("skel-enroll","io.syspulse.skel.enroll.App"),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libAkkaPersistence,
      libAkkaPersistenceTest,
      libAkkaSerJackon,
      libAkkaPersistJDBC,
      libSlick,
      libSlickHikari,
      libH2,
      libLevelDB
      //libAkkaSerJackon
    ),
  )

lazy val pdf = (project in file("skel-pdf"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    
    
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    //name := "skel-pdf",
    appDockerConfig("skel-pdf","io.syspulse.skel.pdf.App"),

    libraryDependencies ++= libPdfGen ++ Seq(
      libScalaTest,
      libLaikaCore,
      libLaikaIo      
    ),
  )


lazy val yell = (project in file("skel-yell"))
  .dependsOn(core,auth_core,ingest,ingest_elastic,ingest_flow)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-yell","io.syspulse.skel.yell.App"),

    libraryDependencies ++= libHttp ++ libTest ++ Seq(
      libAlpakkaElastic,
      libElastic4s
    ),  
  )

lazy val video = (project in file("skel-video"))
  .dependsOn(core,auth_core,ingest,ingest_flow,ingest_elastic)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-video","io.syspulse.skel.video.App"),

    libraryDependencies ++= libCommon ++ libSkel ++ 
      Seq(
        libElastic4s,
        libAkkaHttpSpray,
        libUUID,
      ),
  )

lazy val skel_notify = (project in file("skel-notify"))
  .dependsOn(core,auth_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-notify","io.syspulse.skel.notify.App"),

    libraryDependencies ++= libHttp ++ libDB ++ libTest ++ Seq(
      libAWSJavaSNS,
      libCourier
    ),    
  )
