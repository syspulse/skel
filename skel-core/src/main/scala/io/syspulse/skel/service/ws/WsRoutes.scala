package io.syspulse.skel.service.ws

import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._

import io.syspulse.skel.service.CommonRoutes
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.OverflowStrategy
import akka.NotUsed
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.util.{Success,Failure}
import io.syspulse.skel.service.Routeable
import akka.actor.typed.scaladsl.ActorContext

class WsRoutes(uri:String)(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {
  implicit val system: ActorSystem[_] = context.system
  implicit val ex = system.executionContext
  implicit val mat = ActorMaterializer()(system.classicSystem)

  def ws:WebSocket = new WebSocketEcho()

  val routes: Route =
    pathPrefix(uri) { 
      handleWebSocketMessages(ws.listen())
  }
}
