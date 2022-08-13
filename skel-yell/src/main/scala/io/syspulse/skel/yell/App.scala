package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest.IngestFlow

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",

  elasticUri:String = "",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "",
  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",

  datastore:String = "",

  cmd:String = "",
  params: Seq[String] = Seq(),
)

object App {
  
  def main(args:Array[String]): Unit = {
    println(s"args: '${args.mkString(",")}'")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skell-yell","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/yell)"),
        
        ArgString('f', "feed","Input Feed (def: )"),

        ArgString('_', "elastic.uri","Elastic uri (def: http://localhost:9200)"),
        ArgString('_', "elastic.user","Elastic user (def: )"),
        ArgString('_', "elastic.pass","Elastic pass (def: )"),
        ArgString('_', "elastic.index","Elastic Index (def: index)"),

        ArgLong('n', "limit","Limit (def: -1)"),

        ArgString('d', "datastore","datastore [mysql,postgres,mem,cache] (def: mem)"),
        
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Search pattern"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/yell"),

      elasticUri = c.getString("elastic.uri").getOrElse("http://localhost:9200"),
      elasticIndex = c.getString("elastic.index").getOrElse("index"),
      elasticUser = c.getString("elastic.user").getOrElse(""),
      elasticPass = c.getString("elastic.pass").getOrElse(""),
      
      feed = c.getString("feed").getOrElse(""),
      limit = c.getLong("limit").getOrElse(-1L),

      datastore = c.getString("datastore").getOrElse("stdout"),

      expr = c.getString("expr").getOrElse(" "),
      
      cmd = c.getCmd().getOrElse("scan"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    // val flow = config.datastore match {
    //   // case "mysql" | "db" => new OtpStoreDB(c,"mysql")
    //   // case "postgres" => new OtpStoreDB(c,"postgres")
    //   case "stdout" | "elastic" => new YellSink
    //   case _ => {
    //     Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
    //     System.exit(1)
    //   }
    // }
    
    val expr = config.expr + config.params.mkString(" ")

    config.cmd match {
      case "ingest" => new YellFlow().connect(config.elasticUri, config.elasticIndex)
        .from(IngestFlow.fromFile(config.feed))
        .run()

      //case "get" => (new Object with DynamoGet).connect( config.elasticUri, config.elasticIndex).get(expr)
      case "scan" => new YellScan().connect( config.elasticUri, config.elasticIndex).scan(expr)
      case "search" => new YellSearch().connect( config.elasticUri, config.elasticIndex).search(expr)
      case "searches" => new YellSearch().connect( config.elasticUri, config.elasticIndex).searches(expr)
      case "wildcards" => new YellSearch().connect( config.elasticUri, config.elasticIndex).wildcards(expr)
    
    }
  }
}