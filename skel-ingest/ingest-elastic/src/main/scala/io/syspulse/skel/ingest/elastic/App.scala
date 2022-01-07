package io.syspulse.skel.ingest.elastic

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import scopt.OParser

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}


case class Config(
  command:String = "",
  elasticUri:String = "",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "",
  expr:Seq[String] = Seq(),
  limit:Int = -1,
  feed:String = "",
)


object App {
  
  def main(args:Array[String]): Unit = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('i', "index").action((x, c) => c.copy(elasticIndex = x)).text("Index (def: index)"),
        opt[String]('b', "dynamo").action((x, c) => c.copy(elasticUri = x)).text("Dynamo URI (http://localhost:9200)"),
        opt[Int]('l', "limit").action((x, c) => c.copy(limit = x)).text("Limit (def: no limit)"),
        opt[String]('f', "feed").action((x, c) => c.copy(feed = x)).text("Feed File (def: feed/tms-100.xml)"),
        cmd("ingest").action( (_, c) => c.copy(command = "ingest") ).text("Ingestion").children(),
        // cmd("get").action( (_, c) => c.copy(command = "get")).text("Get Object.").children(
        //   arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("expression (e.g. Object id)"),
        // ),
        cmd("scan").action( (_, c) => c.copy(command = "scan")).text("Scan all objects").children(
          arg[String]("<expr>").optional().action((x, c) => c.copy(expr = c.expr :+ x)).text("expression"),
        ),
        cmd("search").action( (_, c) => c.copy(command = "search")).text("Search field").children(
          arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("search pattern (e.g. Avatar)"),
        ),
        cmd("searches").action( (_, c) => c.copy(command = "searches")).text("Search multiple fields").children(
          arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("search pattern (e.g. Drama)"),
        ),
        cmd("wildcards").action( (_, c) => c.copy(command = "wildcards")).text("Wildcard search multiple fields").children(
          arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("search pattern (e.g. Avat*)"),
        )

      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = Config(
          command = { if(! configArgs.command.isEmpty) configArgs.command else configuration.getString("elastic.command").getOrElse("ingest") },
          elasticUri = { if( ! configArgs.elasticUri.isEmpty) configArgs.elasticUri else configuration.getString("elastic.uri").getOrElse("http://localhost:9200") },
          elasticIndex = { if( ! configArgs.elasticIndex.isEmpty) configArgs.elasticIndex else configuration.getString("elastic.index").getOrElse("index") },
          expr = configArgs.expr,
          limit = { if( configArgs.limit != -1) configArgs.limit else configuration.getInt("elastic.limit").getOrElse(-1) },
          feed = { if( ! configArgs.feed.isEmpty) configArgs.feed else configuration.getString("feed").getOrElse("feed/tms-100.xml") },
        )

        println(s"Config: ${config}")

        config.command match {
          case "ingest" => (new Object with ElasticSink).connect(config.elasticUri, config.elasticIndex).run(config.feed)

          //case "get" => (new Object with DynamoGet).connect( config.elasticUri, config.elasticIndex).get(config.expr.mkString(" "))
          case "scan" => (new Object with ElasticScan).connect( config.elasticUri, config.elasticIndex).scan(config.expr.mkString(" "))
          case "search" => (new Object with ElasticSearch).connect( config.elasticUri, config.elasticIndex).search(config.expr.mkString(" "))
          case "searches" => (new Object with ElasticSearch).connect( config.elasticUri, config.elasticIndex).searches(config.expr.mkString(" "))
          case "wildcards" => (new Object with ElasticSearch).connect( config.elasticUri, config.elasticIndex).wildcards(config.expr.mkString(" "))
        
        }
      }
      case _ => 
    }
  }
}