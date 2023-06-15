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
  @volatile var ws:Map[String,WebSocketServer] = Map()

  def +(ws0: WebSocketServer,id:String = "ws") = {
    ws = ws + (id -> ws0)
  }

  def broadcast(topic:String, title:String, msg:String, id:String = "ws"):Try[Unit] = {
    ws.get(id) match {
      case Some(ws) => 
        //Success( ws.broadcastText(s"${topic}: ${title}(${msg})",topic) )
        Success( ws.broadcastText(s"${msg}",topic) )
      case None => Failure(new Exception(s"not initialized: ${ws}"))
    }
  }

  def all(id:String = "ws") = ws.get(id) match {
    case Some(ws) => ws.all()
    case None => Seq()
  } 

}

class WebSocketServer(idleTimeout:Long)(implicit ex:ExecutionContext,mat:ActorMaterializer) extends WebSocket(idleTimeout) {
  override def process(m:Message,a:ActorRef):Message = {
    val txt = m.asTextMessage.getStrictText
    
    // debug
    log.info(s"${a} -> ${txt}")
    m
  }
}

class WsNotifyRoutes(idleTimeout:Long = 1000L*60*60*24, uri:String = "ws")(implicit context: ActorContext[_]) extends WsRoutes(uri)(context) {
  val wsServer:WebSocketServer = new WebSocketServer(idleTimeout)
  
  WS.+(wsServer, uri)

  override def ws:WebSocket = wsServer
}
