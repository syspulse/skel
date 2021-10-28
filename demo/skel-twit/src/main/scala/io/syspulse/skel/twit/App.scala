package io.syspulse.skel.twit

import io.prometheus.client.Counter

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.cron.Cron
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv,ConfigurationProp}

import io.syspulse.skel.flow._

import scopt.OParser
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  
  twitterConsumerKey:String = "",
  twitterConsumerSecret:String = "",
  twitterAccessKey:String = "",
  twitterAccessSecret:String = "",
  
  twitQueue:Long = -1L,

  users:String = "",
  
  cassandraHosts:String = "",
  cassandraSpace:String = "",
  cassandraTable:String = "",
  
  scrapUsers:String = "",
  scrapDir:String = "",
  
  cmd: Seq[String] = Seq(),
)

object App extends skel.Server {

  val metricTwitCount: Counter = Counter.build().name("twit_twitter_total").help("Twitter total events").register()
  val metricScrapCount: Counter = Counter.build().name("twit_scrap_total").help("Scraping total events").register()
  val metricImageCount: Counter = Counter.build().name("twit_image_total").help("Imaged harvested total events").register()
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "http.host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "http.port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "http.uri").action((x, c) => c.copy(uri = x)).text("uri"),

        opt[String]('z', "users").action((x, c) => c.copy(users = x)).text("Tweeter Users comma seperated list)"),
        opt[String]('s', "scrap.users").action((x, c) => c.copy(scrapUsers = x)).text("Tweeter Users to scrap)"),
        opt[String]('d', "scrap.dir").action((x, c) => c.copy(scrapDir = x)).text("Scrap dir (/dev/shm/))"),
        
        opt[String]("twitter.consumer.key").action((x, c) => c.copy(twitterConsumerKey = x)).text("Twitter Consumer Key"),
        opt[String]("twitter.consumer.secret").action((x, c) => c.copy(twitterConsumerSecret = x)).text("Twitter Consumer Secret"),
        opt[String]("twitter.access.key").action((x, c) => c.copy(twitterAccessKey = x)).text("Twitter Access Key"),
        opt[String]("twitter.access.secret").action((x, c) => c.copy(twitterAccessSecret = x)).text("Twitter Access Key"),
        
        opt[Long]("twit.queue").action((x, c) => c.copy(twitQueue = x)).text("queue size"),
        
        opt[String]("cassandra.hosts").action((x, c) => c.copy(cassandraHosts = x)).text("Cassandra Hosts (127.0.0.1:9042)"),
        opt[String]("cassandra.space").action((x, c) => c.copy(cassandraSpace = x)).text("Cassandra space (twit_space)"),
        opt[String]("cassandra.table").action((x, c) => c.copy(cassandraTable = x)).text("Cassandra table (twit)"),

        //opt[String]("format").action((x, c) => c.copy(format = x)).text("format (not supported)"),

        help("help").text(s"${Util.info._1}"),
        arg[String]("...").unbounded().optional().action((x, c) => c.copy(cmd = c.cmd :+ x)).text("commands"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else configuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else configuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else configuration.getString("http.uri").getOrElse("/api/v1/twit") },
          
          twitterConsumerKey = { if(! configArgs.twitterConsumerKey.isEmpty) configArgs.twitterConsumerKey else configuration.getString("twitter.consumer.key").getOrElse("") },
          twitterConsumerSecret = { if(! configArgs.twitterConsumerSecret.isEmpty) configArgs.twitterConsumerSecret else configuration.getString("twitter.consumer.secret").getOrElse("") },
          twitterAccessKey = { if(! configArgs.twitterAccessKey.isEmpty) configArgs.twitterAccessKey else configuration.getString("twitter.access.key").getOrElse("") },
          twitterAccessSecret = { if(! configArgs.twitterAccessSecret.isEmpty) configArgs.twitterAccessSecret else configuration.getString("twitter.access.secret").getOrElse("") },

          twitQueue = { if(configArgs.twitQueue != -1L) configArgs.twitQueue else configuration.getLong("twit.queue").getOrElse(100L) },
          
          users = { if(! configArgs.users.isEmpty) configArgs.users else configuration.getString("users").getOrElse("") },
          scrapUsers = { if(! configArgs.scrapUsers.isEmpty) configArgs.scrapUsers else configuration.getString("scrap.users").getOrElse("") },
          scrapDir = { if(! configArgs.scrapDir.isEmpty) configArgs.scrapDir else configuration.getString("scrap.dir").getOrElse("") },

          //format = { if(! configArgs.format.isEmpty) configArgs.format else configuration.getString("twit.format").getOrElse("csv") },

          //cassandraHosts = { if(! configArgs.cassandraHosts.isEmpty) configArgs.cassandraHosts else configuration.getString("cassandra.hosts").getOrElse(configuration.getString("datastax-java-driver.basic.contact-points").getOrElse("")) },
          cassandraHosts = { if(! configArgs.cassandraHosts.isEmpty) configArgs.cassandraHosts else configuration.getString("cassandra.hosts").getOrElse("") },
          cassandraSpace = { if(! configArgs.cassandraSpace.isEmpty) configArgs.cassandraSpace else configuration.getString("cassandra.space").getOrElse("twit_space") },
          cassandraTable = { if(! configArgs.cassandraTable.isEmpty) configArgs.cassandraTable else configuration.getString("cassandra.table").getOrElse("twit") },

          cmd = { if(configArgs.cmd.size!=0) configArgs.cmd else Seq(configuration.getString("cmd").getOrElse("cassandra")) },
        )

        Console.err.println(s"Config: ${config}")

        config.cmd.headOption.getOrElse("cassandra") match {
          case "twitter" => {
            if(config.users.trim.isEmpty) {
              Console.err.println("No users specified")
              System.exit(1)
            }

            val twitterUsers = new TweetUsers(config.users,config).resolve()
            
            Console.err.println(s"users: ${twitterUsers}")

            if(twitterUsers.size ==0 ) {
              Console.err.println(s"No users resolved: '${config.users}'")
              System.exit(1)
            }

            new TwitFlow(twitterUsers, 
                          config.cassandraHosts, 
                          keySpace = config.cassandraSpace,
                          table = config.cassandraTable,
                          queueLimit = config.twitQueue,
                          scrapUsers = config.scrapUsers.split(",").map(_.trim).toSeq,
                          scrapDir = config.scrapDir)
          }
          case "cassandra" => 
            new CassandraFlow(
                          config.cassandraHosts, 
                          keySpace = config.cassandraSpace,
                          table = config.cassandraTable,
                          queueLimit = config.twitQueue,
                          scrapUsers = config.scrapUsers.split(",").map(_.trim).toSeq,
                          scrapDir = config.scrapDir)

          case c => Console.err.println(s"unknown command: ${c}"); System.exit(1)
        }

        run( config.host, config.port,config.uri,configuration,
          Seq()
        )
      }
      case _ => 
    }
  }
}