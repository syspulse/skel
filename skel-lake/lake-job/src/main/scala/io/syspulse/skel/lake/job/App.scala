package io.syspulse.skel.lake.job

import scala.util.Try
import scala.util.Random
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.lake.job.livy._
import io.syspulse.skel.lake.job.server._
import io.syspulse.skel.lake.job.store._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/job",
  
  datastore:String = "dir://",

  timeout:Long = 10000L,
  poll:Long = 3000L,

  engine:String = "livy://http://emr.hacken.cloud:8998",

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
        ArgString('d', "datastore",s"datastore (mem://,dir://) (def: ${d.datastore})"),
        ArgString('e', "engine",s"Job Engine  (def: ${d.engine})"),
        
        ArgLong('_', "timeout",s"timeout (msec, def: ${d.timeout})"),
        ArgLong('_', "poll",s"poll interval (msec, def: ${d.poll})"),
        
        ArgCmd("server",s"Server"),
        ArgCmd("client",s"Command"),
        ArgCmd("job",s"Jobs"),
        ArgCmd("livy",s"Livy commands"),
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
      poll = c.getLong("poll").getOrElse(d.poll),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    def getStore() = {
      val engine = JobUri(config.engine)

      val store:JobStore = config.datastore.split("://").toList match {
        //case "livy" :: uri => new JobStoreLivy(config.datastore)
        case "dir" :: Nil => new JobStoreDir(engine)
        case "dir" :: dir :: Nil => new JobStoreDir(engine,dir)
        case _ => {
          Console.err.println(s"Unknown datastore: '${config.datastore}")
          sys.exit(1)
        }
      }
      store
    }

    config.cmd match {
      case "server" => 
        val store = getStore()
        run( config.host, config.port, config.uri, c,
          Seq(
            (JobRegistry(store),"JobRegistry",(r, ac) => new JobRoutes(r)(ac) )
          )
        )      
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

      case "livy" => 
        val engine = JobUri(config.engine)

        val r = config.params match {
          case "create" :: name :: Nil => 
            engine.create(name)

          case "create" :: name :: data => 
            engine.create(name,JobEngine.dataToConf(data))
                  
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

            engine.run(Job(xid = xid),src)

          case "pipeline" :: name :: script :: Nil =>
            JobEngine.pipeline(engine,name,script,List(),config.poll)

          case "pipeline" :: name :: script :: data =>
            JobEngine.pipeline(engine,name,script,data,config.poll)

          case _ => 
            engine.all()
            
        }
        
        println(s"\n${r}")
        sys.exit(0)

      case "job" =>
        val store = getStore()        
                            
        val r = config.params.toList match {

          case "src" :: script :: data => 
            JobEngine.toSrc(script,data)            

          case "submit" :: name :: script :: data => 
            
            store.submit(name = name, 
              script = script, 
              conf = JobEngine.dataToConf(data), 
              inputs = JobEngine.dataToVars(data),
              None,
              5000L
            )
            //engine.run(Job(xid = xid),src)

          case "get" :: id :: Nil => 
            store.?(UUID(id))
          
          case "del" :: id :: Nil => 
            store.del(UUID(id))
          
          case _ => 
            store.all
            
        }
        
        println(s"\n${r}")
        sys.exit(0)
    }
  }
}