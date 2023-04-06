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

        def decodeData(data:List[String],confFilter:(String) => Boolean) = {
          data
            .filter(!_.trim.isEmpty)
            .filter(d => confFilter(d.trim))
            .map(d => {
              if(d.startsWith("file://")) {
                val code = os.read(os.Path(d.stripPrefix("file://"),os.pwd))
                code -> ""
              } else {
                d.split("=").toList match {
                  case k :: v :: Nil => k -> v
                  case _ => d -> ""
                }
              }
            })
            .toMap
        }

        def dataToVars(data:List[String]) = decodeData(data,(d) => {! d.startsWith("spark.")}) 

        def dataToConf(data:List[String]) = decodeData(data,(d) => { d.startsWith("spark.")})           

        def pipeline(name:String,script:String,data:List[String] = List()) = {
          // to_date_input = "2023-02-28"
          // custom_locks={"0x77730ed992d286c53f3a0838232c3957daeaaf73":"veSOLID","0x0000000000000000000000000000000000000000":"mint/burn"}
          // custom_locks = {
          //     '0x77730ed992d286c53f3a0838232c3957daeaaf73': 'veSOLID',
          // }
          // 
          var srcVars = dataToVars(data).map( _ match {
            case(code,"") =>
              code
            case(name,value) =>
              s"${name} = ${value}"
          }).mkString("\n")

          val src = srcVars + "\n" +
            os.read(os.Path(script.stripPrefix("file://"),os.pwd))

          log.info(s"src=${src}")
          
          for {
            j1 <- engine.create(name,dataToConf(data))

            j2 <- {
              var j:Try[Job] = engine.get(j1)
              while(j.isSuccess && j.get.state == "starting") {                  
                Thread.sleep(config.poll)
                j = engine.get(j1)
              } 
              j
            }
            
            j3 <- {
              engine.run(j2,src)
            }

            j4 <- {
              var j:Try[Job] = engine.ask(j3)

              while(j.isSuccess && j.get.state != "available") {
                Thread.sleep(config.poll)
                j = engine.ask(j3)
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
            engine.create(name,dataToConf(data))
                  
          case "get" :: xid :: Nil => 
            engine.get(Job(xid=xid))

          case "ask" :: xid :: Nil => 
            engine.ask(Job(xid=xid))

          case "del" :: xid :: Nil => 
            engine.del(Job(xid=xid))

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
            
        }
        
        println(s"\n${r}")
        sys.exit(0)
    }
  }
}