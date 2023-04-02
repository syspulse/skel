package io.syspulse.skel.lake.job

import scala.util.Random

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.lake.job.livy._
import scala.util.Try

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/lake",
  datastore:String = "all",

  timeout:Long = 10000L,
  poll:Long = 3000L,

  jobEngine:String = "livy://http://emr.hacken.cloud:8998",

  cmd:String = "job",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"lake-job","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [all] (def: ${d.datastore})"),
        
        ArgLong('_', "timeout",s"timeout (msec, def: ${d.timeout})"),
        
        ArgCmd("server",s"Server"),
        ArgCmd("client",s"Command"),
        ArgCmd("job",s"Jobs"),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),      
      datastore = c.getString("datastore").getOrElse(d.datastore),      
      timeout = c.getLong("timeout").getOrElse(d.timeout),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    // val store = config.datastore match {
    //   // case "all" => new JobStoreAll
    //   case _ => {
    //     Console.err.println(s"Unknown datastore: '${config.datastore}")
    //     sys.exit(1)
    //   }
    // }

    val engine = JobUri(config.jobEngine)

    config.cmd match {
      case "server" => 
        // run( config.host, config.port,config.uri,c,
        //   Seq(
        //     (Behaviors.ignore,"",(actor,actorSystem) => new WsNotifyRoutes()(actorSystem) ),
        //     (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
        //   )
        // )      
      case "client" => {        
        import io.syspulse.skel.FutureAwaitable._
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = FiniteDuration(10,TimeUnit.SECONDS)

        val r = 
          config.params match {
            case "notify" :: data =>
            case _ => println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        sys.exit(0)
      }

      case "job" =>
        val engine = JobUri(config.jobEngine,config.timeout).asInstanceOf[LivyHttp]

        def dataToMap(data:List[String]) = 
          data
            .mkString(",")
            .split(",")
            .filter(!_.trim.isEmpty)
            .map(kv => kv.split("=").toList match { case k :: v :: Nil => k -> v })
            .toMap

        def pipeline(name:String,script:String,data:List[String] = List()) = {
          var srcVars = dataToMap(data).map{ case(name,value) => {
            s"${name} = ${value}"
          }}.mkString("\n")

          val src = srcVars + "\n" +
            os.read(os.Path(script.stripPrefix("file://"),os.pwd))

          log.info(s"src=${src}")          

          for {
            j1 <- engine.create(name,dataToMap(data))

            j2 <- {
              var j:Try[Job] = engine.get(j1.xid)
              while(j.isSuccess && j.get.state == "starting") {                  
                Thread.sleep(config.poll)
                j = engine.get(j1.xid)
              } 
              j
            }
            j3 <- {
              engine.run(j2,src)                              
            }
            j4 <- {
              var j:Try[Job] = engine.ask(j3.xid)

              while(j.isSuccess && j.get.state != "available") {
                Thread.sleep(config.poll)
                j = engine.ask(j3.xid)                  
              } 
              j
            }
            j5 <- {
              j4.result match {
                case Some("error") => 
                  log.error(s"Job: Error=${j4.output.getOrElse("")}")
                case Some("ok") =>
                  log.info(s"Job: OK=${j4.output.getOrElse("")}")
                case _ => 
                  log.info(s"Job: Unknown=${j4.result}: output=${j4.output.getOrElse("")}")
              }              

              engine.del(j4.xid)
            }
          } yield j4
        }
                            
        val r = config.params match {
          case "create" :: name :: Nil => 
            engine.create(name)

          case "create" :: name :: data => 
            engine.create(name,dataToMap(data))
                  
          case ("ask" | "get") :: xid :: Nil => 
            engine.ask(xid)

          case "del" :: xid :: Nil => 
            engine.del(xid)

          case "run" :: xid :: script => 
            val src = if(script.head.startsWith("file://"))
              os.read(os.Path(script.head.stripPrefix("file://"),os.pwd))
            else
              script.mkString(" ")

            engine.run(xid,src)

          case "pipeline" :: name :: script :: Nil =>
            pipeline(name,script,List())

          case "pipeline" :: name :: script :: data =>
            pipeline(name,script,data)          

          case _ => 
            engine.all()

            println(s"unknown op: ${config.params}")
        }
        
        println(s"\n${r}")
        sys.exit(0)
    }
  }
}