package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest.IngestFlow
import io.syspulse.skel.ingest.flow.Flows
import io.syspulse.skel.yell.store._

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

object App extends skel.Server {
  
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
        ArgString('_', "elastic.index","Elastic Index (def: yell)"),

        ArgLong('n', "limit","Limit (def: -1)"),

        ArgString('d', "datastore","datastore [elastic,mem,cache] (def: mem)"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Multi-Search pattern"),
        ArgCmd("grep","Wildcards search"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/yell"),

      elasticUri = c.getString("elastic.uri").getOrElse("http://localhost:9200"),
      elasticIndex = c.getString("elastic.index").getOrElse("yell"),
      elasticUser = c.getString("elastic.user").getOrElse(""),
      elasticPass = c.getString("elastic.pass").getOrElse(""),
      
      feed = c.getString("feed").getOrElse(""),
      limit = c.getLong("limit").getOrElse(-1L),

      datastore = c.getString("datastore").getOrElse("mem"),

      expr = c.getString("expr").getOrElse(" "),
      
      cmd = c.getCmd().getOrElse("search"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store:YellStore = config.datastore match {
      case "elastic" => new YellStoreElastic(config.elasticUri,config.elasticIndex)
      case "mem" => new YellStoreMem()
      //case "stdout" => new YellStoreStdout
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (YellRegistry(store),"YellRegistry",(r, ac) => new server.YellRoutes(r)(ac) )
          )
        )
      case "ingest" => new YellFlow()
        .connect[YellFlow](config.elasticUri, config.elasticIndex)
        .from(Flows.fromFile(config.feed))        
        .run()

      //case "get" => (new Object with DynamoGet).connect( config.elasticUri, config.elasticIndex).get(expr)
      case "scan" => store.scan(expr)
      case "search" => store.search(expr)
      case "grep" => store.grep(expr)
  
    }
  }
}