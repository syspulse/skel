package io.syspulse.skel.ingest.dynamo

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import scopt.OParser

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}


case class Config(
  command:String = "",
  dynamoUri:String = "",
  dynamoTable:String = "",
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
        opt[String]('t', "table").action((x, c) => c.copy(dynamoTable = x)).text("Table"),
        opt[String]('b', "dynamo").action((x, c) => c.copy(dynamoUri = x)).text("Dynamo URI (http://localhost:8100)"),
        opt[Int]('l', "limit").action((x, c) => c.copy(limit = x)).text("Limit (def: no limit)"),
        opt[String]('f', "feed").action((x, c) => c.copy(feed = x)).text("Feed File (def: feed/tms-100.xml)"),
        cmd("ingest").action( (_, c) => c.copy(command = "ingest") ).text("Ingestion").children(),
        cmd("get").action( (_, c) => c.copy(command = "get")).text("Get Object.").children(
          arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("expression (e.g. Object id)"),
        ),
        cmd("query").action( (_, c) => c.copy(command = "query")).text("Query Object.").children(
          arg[String]("<expr>").unbounded().action((x, c) => c.copy(expr = c.expr :+ x)).text("expression (e.g. Object id)"),
        ),
        cmd("scan").action( (_, c) => c.copy(command = "scan")).text("Scan all").children(
          arg[String]("<expr>").optional().action((x, c) => c.copy(expr = c.expr :+ x)).text("expression (e.g. Object id)"),
        )
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = Config(
          command = { if(! configArgs.command.isEmpty) configArgs.command else configuration.getString("dynamo.command").getOrElse("ingest") },
          dynamoUri = { if( ! configArgs.dynamoUri.isEmpty) configArgs.dynamoUri else configuration.getString("dynamo.uri").getOrElse("http://localhost:8100") },
          dynamoTable = { if( ! configArgs.dynamoTable.isEmpty) configArgs.dynamoTable else configuration.getString("dynamo.table").getOrElse("MOVIE") },
          expr = configArgs.expr,
          limit = { if( configArgs.limit != -1) configArgs.limit else configuration.getInt("dynamo.limit").getOrElse(-1) },
          feed = { if( ! configArgs.feed.isEmpty) configArgs.feed else configuration.getString("feed").getOrElse("feed/tms-100.xml") },
        )

        println(s"Config: ${config}")

        config.command match {
          case "ingest" => (new Object with DynamoSink).connect( config.dynamoUri, config.dynamoTable).run(config.feed)

          case "get" => (new Object with DynamoGet).connect( config.dynamoUri, config.dynamoTable).get(config.expr.mkString(" "))
          case "query" => (new Object with DynamoGet).connect( config.dynamoUri, config.dynamoTable).query(config.expr.mkString(" "))
          case "scan" => (new Object with DynamoGet).connect( config.dynamoUri, config.dynamoTable).scan(config.limit)
        
        }
      }
      case _ => 
    }
  }
}