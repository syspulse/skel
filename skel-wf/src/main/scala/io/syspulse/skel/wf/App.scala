package io.syspulse.skel.wf

import scala.util.Success

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.wf.runtime.thread._
import io.syspulse.skel.wf.runtime.actor._
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.store._
import io.syspulse.skel.wf.registry._
import io.syspulse.skel.wf.exec._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/wf",

  datastore:String = "dir://",
  storeWorkflow:String = "dir://",
  storeState:String = "dir://",
  
  cmd:String = "wf",
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
      new ConfigurationArgs(args,"skel-wf","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('d', "datastore",s"Datastore [dir://] (def: ${d.datastore})"),
        ArgString('_', "store.workflow",s"Workflows store [dir://] (def: ${d.storeWorkflow})"),
        ArgString('_', "store.state",s"Runtime store [dir://] (def: ${d.storeState})"),
        
        ArgCmd("server",s"Server"),        
        
        ArgCmd("registry",s"Show Execs Registry"),

        ArgCmd("wf",s"Workflow subcommands: " +
          s"assemble name 'dsl'  : create Workflow with dsl commands, ex: 'F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))->F-3(TerminateExec())'" +
          s"load <id>            : Load workflow by id from store" +
          s"show <id>            : Show all workflows in store" +
          ""
        ),
        ArgCmd("runtime",s"Runtime subcommands: " +
          s"spawn <id>          : Spawn + start Workflow"+
          s"run <id>            : Spawn + start + wait Workflow"+
          s"start <wid>         : Start spawned Workflowing"+
          s"stop <wid>          : Stop running Workflowing"+
          s"kill <wid>          : Kill with deleting all states and data"+
          s"status [wid]        : Workflowing status"+
          s"emit <wid> <Exec> [data]    : Emit event into Exec (Input: in-0) or running Workflowing"+
          ""
        ),        
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      storeWorkflow = c.getString("store.workflow").getOrElse(d.storeWorkflow),
      storeState = c.getString("store.state").getOrElse(d.storeState),

      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val (storeWorkflow,storeState) = config.datastore.split("://").toList match {
      case "dir" :: Nil => 
        (new WorkflowStoreDir(),new WorkflowStateStoreDir())
      case "dir" :: dir :: Nil =>
        (new WorkflowStoreDir(dir + "/workflows"),new WorkflowStateStoreDir(dir + "/runtime"))
      case _ => {
        val storeWorkflow = config.storeWorkflow.split("://").toList match {
          case "dir" :: dir :: Nil => new WorkflowStoreDir(dir)
          case "dir" :: Nil => new WorkflowStoreDir()
        }

        val storeState = config.storeState.split("://").toList match {
          case "dir" :: dir :: Nil => new WorkflowStateStoreDir(dir)
          case "dir" :: Nil => new WorkflowStateStoreDir()
          case "mem" :: Nil => new WorkflowStateStoreMem()
          case Nil => new WorkflowStateStoreDir()
        }

        (storeWorkflow,storeState)
      }
    }

    val r = config.cmd match {
      case "server" => 

      case "registry" | "reg" => 
        val reg = new WorkflowRegistry()
        reg.execs.mkString("\n")

      case "wf" => config.params match {
        case ("assemble" | "assembly") :: name :: dsl => 
          val wf = for {
            wf <- Workflow.assemble(s"${name}",name,dsl.mkString(" "))
            wf <- storeWorkflow.+(wf).map(_ => wf)
          } yield wf
          wf
        case "load" :: id :: Nil => 
          val wf = storeWorkflow.?(id)
          wf   
        case _ => 
          storeWorkflow.all
      } 

      case "runtime" => {
        def prompt() = {
          Console.err.println("press [Enter] to gracefully continue, or [CTRL+C] to abort")
          Console.in.readLine()
        }

        implicit val we = new WorkflowEngine(storeWorkflow,storeState,runtime = new RuntimeThreads())
        config.params match {
          case "spawn" :: id :: Nil =>             
            for {
              wf <- storeWorkflow.?(id)          
              wr <- we.spawn(wf)            
            } yield wr
          
          case "status" :: wid :: Nil => 
            for {
              ws <- storeState.?(wid)            
            } yield s"${ws.id}: ${ws.status}"

          case "status" :: Nil =>
            storeState.all.map(ws => s"${ws.id}: ${ws.status}").mkString("\n")

          case "recover" :: id =>
            (id match {
              case Nil => storeState.all.map(_.id)
              case wids => wids.toSeq              
            }).map(id => {
              for {
                w <- we.respawn(id)
                w <- we.start(w)
              } yield w
            })
            
            prompt()

          case "start" :: id :: Nil => 
            for {
              w <- we.respawn(id)
              w <- we.start(w)
            } yield w            
            
          case "stop" :: id :: Nil => 
            for {
              w <- we.respawn(id)
              w <- we.stop(w)
            } yield w

          case "kill" :: id :: Nil => 
            we.kill(id)            

          case "run" :: id :: Nil => 
            val wr = for {
              wf <- storeWorkflow.?(id)
              w <- we.spawn(wf)
              w <- we.start(w)
            } yield w

            prompt()

            we.stop(wr.get)

          case "emit" :: id :: exec :: data =>             
            val wr =for {
              w <- we.respawn(id)
              w <- we.start(w)
            } yield w

            val exData = data match {
              case Nil => Map[String,Any]()
              case data => data.mkString("").split("[,=]").grouped(2).map(a => a(0) -> a(1)).toMap              
            }
            val r = wr.get.emit(exec,"in-0",ExecDataEvent(ExecData(exData)))
            Console.err.println(s"${r}")

            prompt()            

          case _ => 
            for {
              st <- storeState.all
            } yield st
      }}
    }   
    println(s"${r}")
    sys.exit(0) 
  }
}



