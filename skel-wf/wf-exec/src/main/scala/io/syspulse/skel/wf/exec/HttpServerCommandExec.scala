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


class HttpServerCommand extends HttpServer("Command",(http,r,ac) => new HttpRoutes(http,r)(ac))

class HttpServerCommandExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) 
  extends HttpServerExec(wid,name,dataExec) {

  override def getServer():HttpServer = new HttpServerCommand()

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    
    http.take() match {
      case cmd @ "1" => 
        log.info(s"cmd=${cmd}")
        val data1 = data.copy( attr = data.attr + ("input" -> cmd))

        broadcast(data1)
        Success(ExecDataEvent(data1))

      case cmd => 
        // nothing to do, stay
        log.info(s"Unknown command: ${cmd}")
        Failure(new Exception(s"Unknown command: ${cmd}"))
    }
  }
}


