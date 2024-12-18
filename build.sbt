import scala.sys.process.Process
import Dependencies._
import com.typesafe.sbt.packager.docker.DockerAlias
import com.typesafe.sbt.packager.docker._

Global / semanticdbEnabled := true
Global / onChangedBuildSource := ReloadOnSourceChanges

// https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html#Built-in+Tags+and+Rules
Test / parallelExecution := true
//test / parallelExecution := false
// I am sorry sbt, this is stupid ->
// Non-concurrent execution is needed for Server with starting / stopping HttpServer
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

// Set individual project to use scalapb
// Compile / PB.targets := Seq(
//   scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
//   scalapb.gen(grpc=false) -> (Compile / sourceManaged).value
// )


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
// Install emulators:
// docker run -it --rm --privileged tonistiigi/binfmt --install all
//
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
  // dockerBaseImage := "openjdk:18-slim",
  dockerBaseImage := "openjdk-s3fs:11-slim",  // WARNING: this image is needed for JavaScript Nashorn !
  // Add S3 mount options
  // Requires running docker: 
  // --privileged -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY -e S3_BUCKET=haas-data-dev
  bashScriptExtraDefines += """/mount-s3.sh""",
  // bashScriptExtraDefines += """ls -l /mnt/s3/""",
  
  dockerUpdateLatest := true,
  dockerUsername := Some("syspulse"),
  dockerExposedVolumes := Seq(s"${appDockerRoot}/logs",s"${appDockerRoot}/conf",s"${appDockerRoot}/data","/data"),
  //dockerRepository := "docker.io",
  dockerExposedPorts := Seq(8080),

  Docker / defaultLinuxInstallLocation := appDockerRoot,

  // Docker / daemonUserUid := None,
  // Docker / daemonUser := "daemon",

  // Experiments with S3 mount compatibility
  Docker / daemonUserUid := Some("1000"),  
  // Docker / daemonUser := "ubuntu",
  // Docker / daemonGroupGid := Some("1000"),
  // Docker / daemonGroup := "ubuntu",
  
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
    scalaVersion    := Dependencies.scala,
    name            := "skel",
    version         := skelVersion,

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

    // needed to fix error with quill-jasync
    // org.scala-lang.modules:scala-java8-compat_2.13:1.0.2 (early-semver) is selected over {1.0.0, 0.9.1}
    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always
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


val sharedConfigPlugin = Seq(
  assembly / assemblyMergeStrategy := {
      case PathList("META-INF/MANIFEST.MF", xs @ _*) => MergeStrategy.concat
      case x => {
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
      }
  }
)

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

val sharedConfigAssemblySparkAWS = Seq(
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
      f.data.getName.contains("commons-logging-1.1.3.jar") ||
      f.data.getName.contains("aws-java-sdk-bundle-1.11.563.jar") ||
      f.data.getName.contains("jcl-over-slf4j-1.7.30.jar") ||
      (f.data.getName.contains("netty") && (f.data.getName.contains("4.1.50.Final.jar") || (f.data.getName.contains("netty-all-4.1.68.Final.jar"))))

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
      // f.data.getName.contains("commons-logging-1.1.3.jar") ||
      f.data.getName.contains("aws-java-sdk-bundle-1.11.563.jar") ||
      f.data.getName.contains("jcl-over-slf4j-1.7.30.jar") ||
      //(f.data.getName.contains("netty") && (f.data.getName.contains("4.1.50.Final.jar") || (f.data.getName.contains("netty-all-4.1.68.Final.jar"))))
      f.data.getName.contains("netty") && f.data.getName.contains("4.1.50.Final.jar")

      //|| f.data.getName == "spark-core_2.11-2.0.1.jar"
    }
  },
  
  assembly / test := {}
)


def appDockerConfig(appName:String,appMainClass:String,appConfigs:Seq[String]=Seq.empty) = {  
  Seq(
    name := appName,

    run / mainClass := Some(appMainClass),
    assembly / mainClass := Some(appMainClass),
    Compile / mainClass := Some(appMainClass), // <-- This is very important for DockerPlugin generated stage1 script!
    assembly / assemblyJarName := jarPrefix + appName + "-" + "assembly" + "-"+  skelVersion + ".jar",

    Universal / mappings ++= {
      appConfigs.map(c => (file(baseDirectory.value.getAbsolutePath+"/conf/"+c), "conf/"+c))
    },
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/application.conf") -> "conf/application.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/logback.xml") -> "conf/logback.xml",
    bashScriptExtraDefines += s"""addJava "-Dconfig.file=${appDockerRoot}/conf/application.conf"""",
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=${appDockerRoot}/conf/logback.xml"""",           
  ) 
}

def appAssemblyConfig(appName:String,appMainClass:String) = 
  Seq(
    name := appName,
    run / mainClass := Some(appMainClass),
    assembly / mainClass := Some(appMainClass),
    Compile / mainClass := Some(appMainClass),
    assembly / assemblyJarName := jarPrefix + appName + "-" + "assembly" + "-"+  skelVersion + ".jar",
  )


// ======================================================================================================================
lazy val root = (project in file("."))
  .aggregate(core, skel_serde, skel_cron, skel_video, skel_test, http, auth_core, skel_auth, skel_user, kafka, skel_otp, skel_crypto, skel_dsl, scrap, cli, db_cli,
             skel_plugin,
             ingest_core, 
             ingest_flow,
             ingest_elastic,
             ingest_dynamo,
             ingest_twitter,
             ingest,
             skel_enroll,
             skel_syslog,
             syslog_core,
             skel_notify,
             notify_core,
             skel_tag, 
             skel_telemetry,
             skel_job,
             job_core,
             crypto_kms,
             blockchain_core,
             blockchain_rpc,
             blockchain_evm,
             blockchain_tron,
             skel_dns,
             skel_ai,
             skel_tls,
             tools,
             skel_test
             )
  .dependsOn(core, skel_serde, skel_cron, skel_video, skel_test, http, auth_core, skel_auth, skel_user, kafka, skel_otp, skel_crypto, skel_dsl, scrap, cli, db_cli,
             skel_plugin,
             ingest_core,
             ingest_flow,
             ingest_elastic,
             ingest_dynamo,
             ingest_twitter,
             ingest,
             skel_enroll,
             skel_syslog,
             syslog_core,
             skel_notify,
             notify_core,
             skel_tag, 
             skel_telemetry,
             skel_job,
             job_core,
             blockchain_core,
             blockchain_rpc,
             blockchain_evm,
             blockchain_tron,
             skel_dns,
             skel_ai,
             skel_test
             )  
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
          libScodecBits,
          libUpickleLib,
          
          libDirWatcher,
          libDirWatcherScala,
        ),
    )

// ATTENTION:
// serde currently injects log4j-1.7.2 due to old Hadoop dependency !
// it breaks logging with log4j2 !
// FIXME !
lazy val skel_serde = (project in file("skel-serde"))
  .dependsOn(core) // needed only for App application
  // .disablePlugins(sbtassembly.AssemblyPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      
      appAssemblyConfig("skel-serde","io.syspulse.skel.serde.App"),
      //name := "skel-serde",

      libraryDependencies ++= libCommon ++
        libTest ++ 
        Seq(
          libUUID, 
          libAvro4s,
          libUpickleLib,
          libScodecBits,

          libParq,
          libHadoop,          
        ),
    )
    
lazy val skel_protobuf = (project in file("skel-protobuf"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      
      appAssemblyConfig("skel-protobuf","io.syspulse.skel.protobuf.App"),
      // name := "skel-serde",

      Compile / PB.protoSources := Seq(sourceDirectory.value / "main" / "proto"),
      Compile / PB.targets := Seq(
        scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
      ),
      Compile / PB.protocOptions += "-I/usr/include",
      Compile / unmanagedSourceDirectories += baseDirectory.value / "target" / "scala-2.13" / "src_managed",
      

      libraryDependencies ++= libCommon ++
        libTest ++ 
        Seq(
          libUUID, 
          
          // libProtobufProtoc,
          // libProtobufJava,          
          "com.google.api.grpc" % "proto-google-common-protos" % "2.43.0",

          "io.grpc" % "grpc-protobuf" % scalapb.compiler.Version.grpcJavaVersion,//"1.66.0",
          "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
          "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,

          libScalapbRuntime 
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

lazy val skel_cron = (project in file("skel-cron"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  // .disablePlugins(sbtassembly.AssemblyPlugin)
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

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
    ),
  )

lazy val auth_core = (project in file("skel-auth/auth-core"))
  .dependsOn(core, skel_test % Test)
  .settings (
    sharedConfig,
    name := "skel-auth-core",
    
    libraryDependencies ++= libJwt ++ Seq(
      libRequests,  // for JWKS http request
      libUpickleLib,
      libCasbin
    ),    
  )

lazy val skel_auth = (project in file("skel-auth"))
  .dependsOn(core,skel_crypto,auth_core,skel_user)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/permissions-model-rbac.conf") -> "conf/permissions-model-rbac.conf",
    Universal / mappings += file(baseDirectory.value.getAbsolutePath+"/conf/permissions-policy-rbac.csv") -> "conf/permissions-policy-rbac.csv",
    
    appDockerConfig(appNameAuth,appBootClassAuth),
    //appAssemblyConfig(appNameAuth,appBootClassAuth),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ libJwt ++ Seq(
      libUpickleLib,
      libRequests,
      libScalaTags,
      libCask,
      libCasbin
    ),    
  )

lazy val skel_otp = (project in file("skel-otp"))
  .dependsOn(core, auth_core, skel_test % Test)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig(appNameOtp,appBootClassOtp),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(      
        libKuroOtp,
        libQR
    ),
  )


lazy val skel_user = (project in file("skel-user"))
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

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(  
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

lazy val skel_crypto = (project in file("skel-crypto"))
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

          libDirWatcher,
          libDirWatcherScala,
        ),
      // this is important option to support latest log4j2 
      assembly / packageOptions += sbt.Package.ManifestAttributes("Multi-Release" -> "true")
    )

lazy val crypto_kms = (project in file("skel-crypto/crypto-kms"))
  .dependsOn(core,skel_crypto)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssemblyTeku,
      //sharedConfigAssembly,
      name := "crypto-kms",
      libraryDependencies ++= Seq() ++ //Seq(libLog4j2Api, libLog4j2Core) ++ 
        libTest ++ libWeb3j ++ Seq(
          libOsLib,
          libUpickleLib,

          libAWSJavaKMS
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
  .dependsOn(core,skel_cron,flow)
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

lazy val ingest_core = (project in file("skel-ingest/ingest-core"))
  .dependsOn(core, skel_serde)
  //.enablePlugins(JavaAppPackaging)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
    //sharedConfigAssembly,
    //appAssemblyConfig("skel-ingest",""),
    name := "ingest-core",

    libraryDependencies ++= libAkka ++ Seq(
      
      libUpickleLib,
      libScalaTest % Test,
    ),        
  )

lazy val ingest_dynamo = (project in file("skel-ingest/ingest-dynamo"))
  .dependsOn(core,skel_video,ingest_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("ingest-dynamo",appBootClassDynamo),

    libraryDependencies ++= libHttp ++ Seq(
      libAlpakkaDynamo
    ),  
  )
 
lazy val ingest_elastic = (project in file("skel-ingest/ingest-elastic"))
  .dependsOn(core, ingest_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    
    appDockerConfig("ingest-elastic",appBootClassElastic),

    libraryDependencies ++= Seq(
      libAlpakkaElastic
    ),  
  )

lazy val ingest_twitter = (project in file("skel-ingest/ingest-twitter"))
  .dependsOn(core, ingest_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    
    appDockerConfig("ingest-twitter",appBootClassElastic),

    libraryDependencies ++= Seq(
      //libTwitter4s, // deprecated, not supported any longer

      libRequests
    ),  
  )

lazy val ingest = (project in file("skel-ingest"))
  .dependsOn(core, skel_serde, ingest_core, ingest_elastic, kafka, ingest_twitter)
  //.enablePlugins(JavaAppPackaging)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
    //sharedConfigAssembly,
    //appAssemblyConfig("skel-ingest",""),
    name := "skel-ingest",

    libraryDependencies ++= libHttp ++ libAkka ++ libAlpakka ++ libPrometheus ++ libDB ++ Seq(
      libAkkaRemote,
      
      libAlpakkaFile,
      //libAlpakkaSlick,      
      
      libAkkaQuartz,      
      libUpickleLib,

      libParq,
      libParqAkka,
      libHadoop,

      libUpickleLib,

      libScalaTest % Test,
    ),        
  )

lazy val ingest_flow = (project in file("skel-ingest/ingest-flow"))
  .dependsOn(core, skel_serde, ingest, ingest_twitter)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    // appAssemblyConfig("ingest-flow","io.syspulse.skel.ingest.flow.App"),    
    appDockerConfig("ingest-flow","io.syspulse.skel.ingest.flow.App"),    

    libraryDependencies ++= Seq(
      libScalaTest % Test,      
    ),        
  )

lazy val ingest_proxy = (project in file("skel-ingest/ingest-proxy"))
  .dependsOn(core, skel_serde, ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("ingest-proxy","io.syspulse.skel.ingest.proxy.App"),    

    libraryDependencies ++= Seq(
      libCsv,
      libScalaTest % Test,      
    ),        
  )


lazy val stream_std = (project in file("skel-stream/stream-std"))
  .dependsOn(core,skel_dsl)
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
  .dependsOn(core,skel_crypto)
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

lazy val skel_db = (project in file("skel-db"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
    name := "skel-db",
    
    libraryDependencies ++= libDB ++
      Seq(

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

lazy val skel_dsl = (project in file("skel-dsl"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging) // for experiments
  .settings (
      sharedConfig,
      
      // Only for experiments
      sharedConfigAssembly,      
      appAssemblyConfig("skel-dsl","io.syspulse.skel.dsl.App"),
      //name := "skel-dsl",

      libraryDependencies ++= libCommon ++ libTest ++
        Seq(
          libUpickleLib,
          "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        ),
    )


lazy val spark_convert = (project in file("skel-spark/spark-convert"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssemblySparkAWS,
    sharedConfigDockerSpark,
    dockerBuildxSettings,

    appDockerConfig("spark-convert","io.syspulse.skel.spark.CsvConvert"),

    version := "0.0.7",

    libraryDependencies ++= libSparkAWS ++ Seq(
      
    ),
  )

lazy val spark_read = (project in file("skel-spark/spark-read"))
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssemblySpark,
    sharedConfigDockerSpark,
    dockerBuildxSettings,

    appDockerConfig("spark-read","io.syspulse.skel.spark.AppSparkRead"),
    
    libraryDependencies ++= libSpark ++ Seq(
      
    ),
  )

lazy val skel_enroll = (project in file("skel-enroll"))
  .dependsOn(core,auth_core,skel_crypto,skel_user,skel_notify,skel_test % Test)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    //name := "skel-enroll",
    appDockerConfig("skel-enroll","io.syspulse.skel.enroll.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
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

    libraryDependencies ++= libHttp ++ libPdfGen ++ Seq(
      libScalaTest,
      libLaikaCore,
      libLaikaIo      
    ),
  )

lazy val syslog_core = (project in file("skel-syslog/syslog-core"))
  .dependsOn(core,auth_core,kafka)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
  
    name := "syslog-core",

    libraryDependencies ++= libSkel ++ libTest ++ Seq(

    ),    
  )


lazy val skel_syslog = (project in file("skel-syslog"))
  .dependsOn(core,syslog_core,auth_core,ingest)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-syslog","io.syspulse.skel.syslog.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libTest ++ Seq(
      libAlpakkaElastic,
      libElastic4s
    ),  
  )

lazy val skel_video = (project in file("skel-video"))
  .dependsOn(core,auth_core,ingest,ingest_elastic)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-video","io.syspulse.skel.video.App"),

    libraryDependencies ++= libCommon ++ 
      Seq(
        libElastic4s,
        libAkkaHttpSpray,
        libUUID,
      ),
  )

lazy val notify_core = (project in file("skel-notify/notify-core"))
  .dependsOn(core,auth_core,kafka)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
  
    name := "notify-core",

    libraryDependencies ++= libSkel ++ libTest ++ Seq(

    ),    
  )

lazy val skel_notify = (project in file("skel-notify"))
  .dependsOn(core,notify_core,auth_core,syslog_core,skel_dsl)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-notify","io.syspulse.skel.notify.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
      libAWSJavaSNS,
      libCourier
    ),    
  )

lazy val skel_tag = (project in file("skel-tag"))
  .dependsOn(core,auth_core,ingest,skel_cron)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-tag","io.syspulse.skel.tag.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
      libElastic4s
    ),    
  )

lazy val skel_telemetry = (project in file("skel-telemetry"))
  .dependsOn(core,auth_core,ingest,cli,skel_cron)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-telemetry","io.syspulse.skel.telemetry.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
      libElastic4s,
      libAlpakkaDynamo
    ),    
  )

lazy val skel_wf = (project in file("skel-wf"))
  .dependsOn(core,notify_core,skel_dsl)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,
    
    appDockerConfig("skel-wf","io.syspulse.skel.wf.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
      libOsLib,
      libUpickleLib,
    )
  )

lazy val job_core = (project in file("skel-job/job-core"))
  .dependsOn(core,auth_core,kafka)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
  
    name := "job-core",

    libraryDependencies ++= libSkel ++ libTest ++ Seq(

    ),    
  )


lazy val skel_job = (project in file("skel-job"))
  .dependsOn(core,job_core,notify_core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,
    
    appDockerConfig("skel-job","io.syspulse.skel.job.App"),

    libraryDependencies ++= libTest ++ Seq(
      libOsLib,
      libUpickleLib,
    )
  )

lazy val skel_odometer = (project in file("skel-odometer"))
  .dependsOn(core,auth_core,skel_cron)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (

    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-odometer","io.syspulse.skel.odometer.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq( 
      libRedis
    ),    
  )

lazy val skel_plugin = (project in file("skel-plugin"))
  .dependsOn(core,skel_dsl)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,
    
    appDockerConfig("skel-plugin","io.syspulse.skel.plugin.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libDB ++ libTest ++ Seq(
      libOsLib,
      libUpickleLib,
    )
  )

lazy val skel_plugin_1 = (project in file("skel-plugin/plugin-1"))
  .dependsOn(skel_plugin)
  .enablePlugins(JavaAppPackaging)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
    sharedConfig,
    sharedConfigAssembly,
    //sharedConfigPlugin,

    // this is required to inject Plugin Metadata
    // 'assembly' may not work, use 'sbt package'
    packageOptions += Package.ManifestAttributes(
      "Plugin-Title" -> "TestPlugin-1",
      "Plugin-Version" -> "1.0.0",
      "Plugin-Class" -> "io.syspulse.skel.plugin.TestPlugin_1"
    ),
    
    name := "plugin-1",
    version := "1.0.0",

    libraryDependencies ++= Seq(      
    )
  )

lazy val blockchain_core = (project in file("skel-blockchain/blockchain-core"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,      
      name := "blockchain-core",
      
      libraryDependencies ++= libTest
    )

lazy val blockchain_evm = (project in file("skel-blockchain/blockchain-evm"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,      
      name := "blockchain-evm",
      
      libraryDependencies ++= libTest
    )

lazy val blockchain_tron = (project in file("skel-blockchain/blockchain-tron"))
  .dependsOn(core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,      
      name := "blockchain-tron",
      
      libraryDependencies ++= libTest
    )

lazy val blockchain_rpc = (project in file("skel-blockchain/blockchain-rpc"))
  .dependsOn(core,skel_crypto,blockchain_core)
  //.disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssemblyTeku,
      //sharedConfigAssembly,
      
      name := "blockchain-rpc",
      
      libraryDependencies ++=  libTest ++ libWeb3j

      // this is important option to support latest log4j2 
      //assembly / packageOptions += sbt.Package.ManifestAttributes("Multi-Release" -> "true")
    )

lazy val ai_core = (project in file("skel-ai/ai-core"))
  .dependsOn(core)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (    
    sharedConfig,
    
    name := "ai-core",

    libraryDependencies ++= libTest ++ Seq(      
    ), 
  )

lazy val ai_agent = (project in file("skel-ai/ai-agent"))
  .dependsOn(core,ai_core)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings (
      sharedConfig,
      name := "ai-agent",
      
      libraryDependencies ++= libAkka ++
        Seq(          
          libScalaLogging,
          libLogback,          
          libOsLib, 
          libRequests,
          libUpickleLib,
          
          //libCequenceOpenAiClient,
          libCequenceOpenAiStream,

          libScalaTest % Test,
        ),

      // AI library dependency override (it forces to akka 2.7)
      dependencyOverrides += "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
    )

lazy val skel_ai = (project in file("skel-ai"))
  .dependsOn(core,auth_core,ai_core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  // .enablePlugins(AshScriptPlugin)
  .settings (
    
    sharedConfig,
    sharedConfigAssembly,
    sharedConfigDocker,
    dockerBuildxSettings,

    appDockerConfig("skel-ai","io.syspulse.skel.ai.App"),

    libraryDependencies ++= libSkel ++ libHttp ++ libTest ++ Seq(

    ),  
  )

lazy val skel_dns = (project in file("skel-dns"))
  .dependsOn(core)
  .disablePlugins(sbtassembly.AssemblyPlugin)
    .settings (    
    sharedConfig,
    //sharedConfigAssembly,
    
    name := "skel-dns",
    //appDockerConfig("skel-dns","io.syspulse.skel.dns.App"),

    libraryDependencies ++= libSkel ++ libTest ++ Seq(
      libRequests,
      libApacheCommonsNet,
      libDnsJava
    ),  
  )

lazy val skel_tls = (project in file("skel-tls"))
  .dependsOn(core)
  .disablePlugins(sbtassembly.AssemblyPlugin)  
  .settings (
      sharedConfig,
      name := "skel-tls",
      
      libraryDependencies ++= 
        Seq(
          libScalaLogging,
          libLogback,
          libUUID,
          libOsLib,
          libScalaTest % Test
        ),
    )

lazy val tools = (project in file("tools"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings (
      sharedConfig,
      sharedConfigAssembly,
      sharedConfigDocker,
      dockerBuildxSettings,

      appDockerConfig("skel-tools","io.syspulse.skel.tools.HttpServer"),
      //name := "skel-tools",

      libraryDependencies ++= 
        Seq(
          libCask, 
          libOsLib,
          libUpickleLib
        ),
    )
