package io.syspulse.skel.wf.exec

import scala.util.Random

import scala.collection.immutable.Queue
import java.util.concurrent.BlockingDeque
import java.util.concurrent.{LinkedBlockingQueue,ArrayBlockingQueue}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.Future
import scala.util.{ Try, Failure, Success }
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import akka.util.ByteString

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.syspulse.skel.util.Util
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util
import io.syspulse.skel.notify.client._

import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import io.syspulse.skel
import io.syspulse.skel.config._


object HttpServerRegistry {  
  val log = Logger(s"${this}")
  
  def apply(): Behavior[skel.Command] = {
    registry()
  }

  private def registry(): Behavior[skel.Command] = {
    
    Behaviors.receiveMessage {
      // case GetUsers(replyTo) =>
      //   replyTo ! Users(store.all)
      //   Behaviors.same

      // case GetUser(id, replyTo) =>
      //   replyTo ! store.?(id)
      //   Behaviors.same
      case _ => 
        Behaviors.same
    }
  }
}

class HttpRoutes(http:HttpServer,registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {
  val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
    
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  
  def getRoute(msg: String) = get {
    log.info(s"=> ${msg}")
    http.push(msg)
    complete("OK")
  }

  def getRoute() = get {
    log.info(s"=>")
    complete("OK")
  }
  
  // def postRoute = post {
  //   entity(as[UserCreateReq]) { req =>
  //     onSuccess(createUser(req)) { r =>
  //       metricCreateCount.inc()
  //       complete(StatusCodes.Created, r)
  //     }
  //   }
  // }
  
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
      concat(
        pathEndOrSingleSlash {
          concat(
            getRoute()
            // postRoute()
          )
        },        
        pathPrefix(Segment) { msg => 
          pathEndOrSingleSlash {
            // postRoute(id) ~
            getRoute(msg)
          }
        }
      )
  }
}

class HttpServer(name:String,routes: (HttpServer,ActorRef[Command],ActorContext[_])=>Routeable) extends skel.Server {
  implicit val as:actor.ActorSystem = actor.ActorSystem(s"HttpServer-${name}")
  implicit val ec = as.getDispatcher

  val queue = new LinkedBlockingQueue[String](10) //Queue[String]()

  def push(cmd:String) = {
    queue.offer(cmd) match {
      case false => 
        log.warn(s"Queue is full: ${cmd}")
      case _ =>
        // nothing to do
    }
  }

  def take() = {
    queue.take        
  }

  @volatile
  var started = false

  def start(host:String,port:Int,uri:String, c:Configuration) = this.synchronized { if(! started) {
    started = true
    run( host, port, uri, c,
      Seq(
        (HttpServerRegistry(),s"HttpRegistry-${name}",(r, ac) => routes(this,r,ac) )
      )
    )
  }}
}

abstract class HttpServerExecuting(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) 
  extends Executing(wid,name,dataExec ++ Map("retry.max" -> 10)) {
  
  val host = dataExec.get("http.host").getOrElse("0.0.0.0").asInstanceOf[String]
  val port = dataExec.get("http.port").getOrElse(8080).asInstanceOf[Int]
  val uri = dataExec.get("http.uri").getOrElse("/").asInstanceOf[String]
  val auth = dataExec.get("http.auth").getOrElse("").asInstanceOf[String]
  
  
  val http = getServer()
  val timeout = FiniteDuration(dataExec.get("http.timeout").getOrElse(3000L).asInstanceOf[Long],TimeUnit.MILLISECONDS)
    
  http.start(host, port, uri, 
    Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv)
    )
  )

  def getServer():HttpServer
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent]
}

class HttpServerExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) 
  extends HttpServerExecuting(wid,name,dataExec) {
  
  // default server with only GET
  def getServer():HttpServer = new HttpServer("",(http,r,ac) => new HttpRoutes(http,r)(ac))
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    
    val msg = http.take()
    log.info(s"msg=${msg}")
    val data1 = data.copy( attr = data.attr + ("input" -> msg))

    broadcast(data1)
    Success(ExecDataEvent(data1))    
  }
}
