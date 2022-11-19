package io.syspulse.skel.notify.server

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext

import io.syspulse.skel.service.ws.WsRoutes
import io.syspulse.skel.service.ws.WebSocket
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext

object WS {  
  @volatile var ws:Option[WebSocketServer] = None

  def +(ws0: WebSocketServer) = {
    ws = Some(ws0)
  }

  def broadcast(topic:String,title:String,msg:String):Try[Unit] = {
    ws match {
      case Some(ws) => Success( ws.broadcastText(s"${topic}: ${title}(${msg})",topic) )
      case None => Failure(new Exception(s"not initialized: ${ws}"))
    }
  }

}

class WebSocketServer()(implicit ex:ExecutionContext,mat:ActorMaterializer) extends WebSocket() {
  override def process(m:Message,a:ActorRef):Message = {
    val txt = m.asTextMessage.getStrictText
    
    // debug
    log.info(s"${a} -> ${txt}")
    m
  }
}

class WsNotifyRoutes()(implicit context: ActorContext[_]) extends WsRoutes("ws")(context) {
  val wsServer:WebSocketServer = new WebSocketServer()
  WS.+(wsServer)

  override def ws:WebSocket = wsServer
}
