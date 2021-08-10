import sbt._

object Dependencies {

    // Versions
    lazy val versionScalaLogging = "3.9.2"
    lazy val akkaVersion    = "2.6.14"
    lazy val alpakkaVersion = "3.0.0"  
    lazy val akkaHttpVersion = "10.2.1"
    lazy val akkaKafkaVersion = "2.0.3"
    lazy val kafkaAvroSerVersion = "5.4.1"
    lazy val quillVersion = "3.6.0"
    
    lazy val appNameHttp = "skel-http"
    lazy val appBootClassHttp = "io.syspulse.skel.service.App"
    
    lazy val appNameOtp = "skel-otp"
    lazy val appBootClassOtp = "io.syspulse.skel.otp.App"

    lazy val appNameAuth = "skel-auth"
    lazy val appBootClassAuth = "io.syspulse.skel.auth.App"
    lazy val appNameUser = "skel-user"
    lazy val appBootClassUser = "io.syspulse.skel.user.App"
    lazy val appNameKafka = "skel-kafka"
    lazy val appBootClassKafka = "io.syspulse.skel.kafka.App"

    lazy val appNameWorld = "skel-world"
    lazy val appBootClassWorld = "io.syspulse.skel.world.App"

    lazy val appNameShop = "skel-shop"
    lazy val appBootClassShop = "io.syspulse.skel.shop.App"

    // lazy val appNameTelemetry = "skel-telemetry"
    // lazy val appBootClassTelemetry = "io.syspulse.ekm.App"

    lazy val appNameEkm = "skel-ekm"
    lazy val appBootClassEkm = "io.syspulse.ekm.App"

    
    lazy val appVersion = "0.0.2"
    lazy val jarPrefix = "server-"
    
    lazy val appDockerRoot = "/app"

    // Akka Libraries
    val libAkkaActor =      "com.typesafe.akka"           %% "akka-actor"           % akkaVersion
    val libAkkaActorTyped = "com.typesafe.akka"           %% "akka-actor-typed"     % akkaVersion
    val libAkkaCluster =    "com.typesafe.akka"           %% "akka-cluster"         % akkaVersion
    val libAkkaHttp =       "com.typesafe.akka"           %% "akka-http"            % akkaHttpVersion
    val libAkkaHttpSpray =  "com.typesafe.akka"           %% "akka-http-spray-json" % akkaHttpVersion
    val libAkkaStream =     "com.typesafe.akka"           %% "akka-stream"          % akkaVersion

    val libAlpakkaInfluxDB ="com.lightbend.akka"           %% "akka-stream-alpakka-influxdb"    % alpakkaVersion

    val libAkkaProtobuf =   "com.typesafe.akka"           %% "akka-protobuf"        % akkaVersion
    val libAkkaKafka=       "com.typesafe.akka"           %% "akka-stream-kafka"    % akkaKafkaVersion

    val libScalaLogging =   "com.typesafe.scala-logging"  %% "scala-logging"        % "3.9.2"
    val libLogback =        "ch.qos.logback"              %  "logback-classic"      % "1.2.3"

    val libQuill =          "io.getquill"                 %% "quill-jdbc"           % "3.5.2"
    val libMySQL =          "mysql"                       %  "mysql-connector-java" % "8.0.22"

    val libTypesafeConfig = "com.typesafe"                %  "config"               % "1.4.1"
      
    val libWsRs =           "javax.ws.rs"                 % "javax.ws.rs-api"       % "2.0.1"
    val libSwaggerAkkaHttp ="com.github.swagger-akka-http" %% "swagger-akka-http"   % "2.3.0"
    val libMetrics =        "nl.grons"                    %% "metrics4-scala"       % "4.1.14"

      // "org.backuity.clist" %% "clist-core"               % "3.5.1",
      // "org.backuity.clist" %% "clist-macros"             % "3.5.1" % "provided",
    val libScopt =          "com.github.scopt"            %% "scopt"                % "4.0.0"

    val libUUID =           "io.jvm.uuid"                 %% "scala-uuid"           % "0.3.1"

    val libKafkaAvroSer =   "io.confluent"                % "kafka-avro-serializer" % kafkaAvroSerVersion

    val libScalaTest =      "org.scalatest"               %% "scalatest"            % "3.1.2"// % Test
    //val libSpecs2core =     "org.specs2"                  %% "specs2-core"          % "2.4.17"
    val libAkkaTestkit =    "com.typesafe.akka"           %% "akka-http-testkit"    % akkaHttpVersion// % Test
    val libAkkaTestkitType ="com.typesafe.akka"           %% "akka-actor-testkit-typed" % akkaVersion// % Test
    
    //val libJline =          "org.jline"                   %  "jline"                 % "3.14.1"
    //val libJson4s =         "org.json4s"                  %%  "json4s-native"        % "3.6.7"
    val libOsLib =          "com.lihaoyi"                 %% "os-lib"               % "0.7.7"
    val libUpickleLib =     "com.lihaoyi"                 %% "upickle"              % "1.3.15"
    val libUjsonLib =       "com.lihaoyi"                 %% "ujson"                % "1.3.15"

    val libCsv =            "com.github.tototoshi"          %% "scala-csv"            % "1.3.7"
    val libFaker =          "com.github.javafaker"          % "javafaker"             % "1.0.2"

    val libPrometheusClient =   "io.prometheus"             % "simpleclient"              % "0.10.0"
    val libPrometheusHttp =     "io.prometheus"             % "simpleclient_httpserver"   % "0.10.0"
    val libPrometheusHotspot =  "io.prometheus"             % "simpleclient_hotspot"   % "0.10.0"
    //val libPrometheusPushGw = "io.prometheus"               % "simpleclient_pushgateway"   % "0.10.0"
    
    // This is modified version for Scala2.13 (https://github.com/syspulse/kuro-otp)
    val libKuroOtp =        "com.ejisan"                    %% "kuro-otp"           % "0.0.3-SNAPSHOT"
    val libQR =             "net.glxn"                      % "qrgen"               % "1.4"

    // Projects
    val libAkka = Seq(libAkkaActor,libAkkaActorTyped,libAkkaStream)
    val libAlpakka = Seq(libAlpakkaInfluxDB)
    val libHttp = Seq(libAkkaHttp,libAkkaHttpSpray)
    val libCommon = Seq(libScalaLogging, libLogback, libTypesafeConfig )
    
    val libTest = Seq(libScalaTest % Test,libAkkaTestkit % Test,libAkkaTestkitType % Test)
    val libTestLib = Seq(libScalaTest,libAkkaTestkit,libAkkaTestkitType)

    val libSkel = Seq(libWsRs,libSwaggerAkkaHttp,libMetrics,libScopt,libUUID)

    val libDB = Seq(libQuill,libMySQL)

    val libPrometheus = Seq(libPrometheusClient,libPrometheusHttp,libPrometheusHotspot)
    val libLihaoyi = Seq(libOsLib,libUpickleLib,libUjsonLib)
  }
  