import sbt._

object Dependencies {

    // Versions
    lazy val versionScalaLogging = "3.9.2"
    lazy val akkaVersion    = "2.6.19"
    lazy val alpakkaVersion = "3.0.4"  
    lazy val akkaHttpVersion = "10.2.4"
    lazy val akkaKafkaVersion = "2.0.3"
    lazy val kafkaAvroSerVersion = "5.4.1"
    lazy val quillVersion = "3.6.0"
    lazy val influxDBVersion = "3.2.0"
    
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

    lazy val appNameIngest = "skel-ingest"
    // lazy val appBootClassTelemetry = "io.syspulse.ekm.App"

    lazy val appNameEkm = "skel-ekm"
    lazy val appBootClassEkm = "io.syspulse.ekm.App"

    lazy val appNameScrap = "skel-scrap"
    lazy val appBootClassScrap = "io.syspulse.skel.scrap.demo.App"

    lazy val appNameNpp = "skel-npp"
    lazy val appBootClassNpp = "io.syspulse.skel.npp.App"

    lazy val appNameCron = "skel-cron"
    lazy val appBootClassCron = "io.syspulse.skel.cron.App"

    lazy val appNameTwit = "skel-twit"
    lazy val appBootClassTwit = "io.syspulse.skel.twit.App"
    
    lazy val appNameDynamo = "skel-dynamo"
    lazy val appBootClassDynamo = "io.syspulse.skel.ingest.dynamo.App"

    lazy val appNameElastic = "skel-elastic"
    lazy val appBootClassElastic = "io.syspulse.skel.ingest.elastic.App"


    lazy val appVersion = "0.0.5"
    lazy val jarPrefix = "server-"
    
    lazy val appDockerRoot = "/app"

    // Akka Libraries
    val libAkkaActor =      "com.typesafe.akka"           %% "akka-actor"           % akkaVersion
    val libAkkaActorTyped = "com.typesafe.akka"           %% "akka-actor-typed"     % akkaVersion
    val libAkkaCluster =    "com.typesafe.akka"           %% "akka-cluster"         % akkaVersion
    val libAkkaHttp =       "com.typesafe.akka"           %% "akka-http"            % akkaHttpVersion
    val libAkkaHttpSpray =  "com.typesafe.akka"           %% "akka-http-spray-json" % akkaHttpVersion
    val libAkkaStream =     "com.typesafe.akka"           %% "akka-stream"          % akkaVersion

    val libAlpakkaInfluxDB ="com.lightbend.akka"          %% "akka-stream-alpakka-influxdb"       % alpakkaVersion
    val libAlpakkaCassandra="com.lightbend.akka"          %% "akka-stream-alpakka-cassandra"      % alpakkaVersion //"2.0.2"
    val libAlpakkaDynamo=   "com.lightbend.akka"          %% "akka-stream-alpakka-dynamodb"       % alpakkaVersion
    val libAlpakkaElastic=  "com.lightbend.akka"          %% "akka-stream-alpakka-elasticsearch"  % alpakkaVersion
    val libAlpakkaMQTT=     "com.lightbend.akka"          %% "akka-stream-alpakka-mqtt-streaming" % alpakkaVersion

    val libAkkaProtobuf =   "com.typesafe.akka"           %% "akka-protobuf"        % akkaVersion
    val libAkkaKafka=       "com.typesafe.akka"           %% "akka-stream-kafka"    % akkaKafkaVersion

    val libScalaLogging =   "com.typesafe.scala-logging"  %% "scala-logging"        % "3.9.2"
    val libLogback =        "ch.qos.logback"              %  "logback-classic"      % "1.2.8"
    val libJanino =         "org.codehaus.janino"         %  "janino"               % "3.1.6"
    // I need this rubbish slf4j to deal with old jboss dependecny which generates exception in loading logback.xml
    //val libSlf4jApi =       "org.slf4j"                   %  "slf4j-api"            % "1.8.0-beta4"
    // Supports only old XML Config file format
    val libSlf4jApi =       "org.slf4j"                   %  "slf4j-api"            % "1.7.26"
    // Needed for teku
    val libLog4j2Api =      "org.apache.logging.log4j" % "log4j-api" % "2.17.2"
    val libLog4j2Core =     "org.apache.logging.log4j" % "log4j-core" % "2.17.2"

    val libQuill =          "io.getquill"                 %% "quill-jdbc"           % "3.5.2"
    val libMySQL =          "mysql"                       %  "mysql-connector-java" % "8.0.22"
    val libPostgres =       "org.postgresql"              % "postgresql"            % "42.3.5"
    val libInfluxDB =       "com.influxdb"                %% "influxdb-client-scala" % influxDBVersion

    val libTypesafeConfig = "com.typesafe"                %  "config"               % "1.4.1"
      
    val libWsRs =           "javax.ws.rs"                 % "javax.ws.rs-api"       % "2.0.1"
    val libSwaggerAkkaHttp ="com.github.swagger-akka-http" %% "swagger-akka-http"   % "2.7.0"
    
    val libMetrics =        "nl.grons"                    %% "metrics4-scala"       % "4.1.14"
    //val libAkkaHttpMetrics ="fr.davit"                    %% "akka-http-metrics-dropwizard" % "1.6.0"
    val libAkkaHttpMetrics ="fr.davit"                    %% "akka-http-metrics-prometheus" % "1.6.0"

      // "org.backuity.clist" %% "clist-core"               % "3.5.1",
      // "org.backuity.clist" %% "clist-macros"             % "3.5.1" % "provided",
    val libScopt =          "com.github.scopt"            %% "scopt"                % "4.0.0"

    val libUUID =           "io.jvm.uuid"                 %% "scala-uuid"           % "0.3.1"

    val libKafkaAvroSer =   "io.confluent"                % "kafka-avro-serializer" % kafkaAvroSerVersion

    val libScalaTest =      "org.scalatest"               %% "scalatest"            % "3.1.2"// % Test
    //val libSpecs2core =     "org.specs2"                  %% "specs2-core"          % "2.4.17"
    val libAkkaTestkit =    "com.typesafe.akka"           %% "akka-http-testkit"    % akkaHttpVersion// % Test
    val libAkkaTestkitType ="com.typesafe.akka"           %% "akka-actor-testkit-typed" % akkaVersion// % Test
    
    val libJline =          "org.jline"                   %  "jline"                 % "3.14.1"
    //val libJson4s =         "org.json4s"                  %%  "json4s-native"        % "3.6.7"
    val libOsLib =          "com.lihaoyi"                 %% "os-lib"               % "0.8.0" //"0.7.7"
    val libUpickleLib =     "com.lihaoyi"                 %% "upickle"              % "1.4.1"
    //val libUjsonLib =       "com.lihaoyi"                 %% "ujson"                % "1.4.1"
    val libScalaTags =      "com.lihaoyi"                 %% "scalatags"            % "0.9.4"
    val libCask =           "com.lihaoyi"                 %% "cask"                 % "0.7.11" // "0.7.8"
    val libRequests =       "com.lihaoyi"                 %% "requests"             % "0.6.9"

    val libCsv =            "com.github.tototoshi"          %% "scala-csv"            % "1.3.7"
    val libFaker =          "com.github.javafaker"          % "javafaker"             % "1.0.2"

    val libPrometheusClient =   "io.prometheus"             % "simpleclient"              % "0.10.0"
    val libPrometheusHttp =     "io.prometheus"             % "simpleclient_httpserver"   % "0.10.0"
    val libPrometheusHotspot =  "io.prometheus"             % "simpleclient_hotspot"   % "0.10.0"
    //val libPrometheusPushGw = "io.prometheus"               % "simpleclient_pushgateway"   % "0.10.0"
    
    // This is modified version for Scala2.13 (https://github.com/syspulse/kuro-otp)
    val libKuroOtp =        "com.ejisan"                    %% "kuro-otp"           % "0.0.3-SNAPSHOT"
    val libQR =             "net.glxn"                      % "qrgen"               % "1.4"

    val libWeb3jCrypto =    "org.web3j"                     % "crypto"              % "4.8.7" exclude("org.bouncycastle", "bcprov-jdk15on")
    val libWeb3jCore =      "org.web3j"                     % "core"                % "4.8.7" exclude("org.bouncycastle", "bcprov-jdk15on")
    
    //web3j depends on "1.65"
    val libBouncyCastle =   "org.bouncycastle"              % "bcprov-jdk15on"      % "1.70" //"1.69" 
    
    val libScodecBits =     "org.scodec"                    %% "scodec-bits"        % "1.1.30" //"1.1.12" 
    val libHKDF =           "at.favre.lib"                  % "hkdf"                % "1.1.0"
    val libBLS =            "tech.pegasys.teku.internal"    % "bls"                 % "23.3.1" //"21.9.2"
    val libBLSKeystore =    "tech.pegasys.signers.internal" % "bls-keystore"        % "2.2.1"  //"1.0.21"

    val libScalaScraper =   "net.ruippeixotog"              %% "scala-scraper"      % "2.2.1"

    val libQuartz =         "org.quartz-scheduler"          % "quartz"              % "2.3.2" exclude("com.zaxxer", "HikariCP-java7")

    val libTwitter4s =      "com.danielasfregola"           %% "twitter4s"          % "7.0"
    
    val libSeleniumJava =   "org.seleniumhq.selenium"       % "selenium-java"             % "4.0.0-rc-3"
    val libSeleniumFirefox ="org.seleniumhq.selenium"       % "selenium-firefox-driver"   % "4.0.0-rc-3"

    val libJwtCore =   "com.pauldijou"                 %% "jwt-core"           % "4.2.0"
    val libJoseJwt =   "com.nimbusds"                  % "nimbus-jose-jwt"     % "4.21"

    val libAvro4s =         "com.sksamuel.avro4s"           %% "avro4s-core"        % "4.0.12"

    val libSSSS =           "com.gladow"                    %% "scalassss"          % "0.2.0-SNAPSHOT"

    // Projects
    val libAkka = Seq(libAkkaActor,libAkkaActorTyped,libAkkaStream)
    val libAlpakka = Seq(libAlpakkaInfluxDB)
    val libPrometheus = Seq(libPrometheusClient,libPrometheusHttp,libPrometheusHotspot)
    val libHttp = Seq(libAkkaHttp,libAkkaHttpSpray,libAkkaHttpMetrics) ++ libPrometheus
    val libCommon = Seq(libScalaLogging, libSlf4jApi, libLogback, libJanino, libTypesafeConfig )
    
    val libTest = Seq(libOsLib, libScalaTest % Test,libAkkaTestkit % Test,libAkkaTestkitType % Test)
    val libTestLib = Seq(libScalaTest,libAkkaTestkit,libAkkaTestkitType)

    val libSkel = Seq(libWsRs,libSwaggerAkkaHttp,libMetrics,libScopt,libUUID)

    val libDB = Seq(libQuill,libMySQL, libPostgres)

    val libLihaoyi = Seq(libOsLib,libUpickleLib)

    val libWeb3j = Seq(libBouncyCastle,libWeb3jCore,libWeb3jCrypto)

    val libJwt = Seq(libJwtCore,libJoseJwt)
  }
  