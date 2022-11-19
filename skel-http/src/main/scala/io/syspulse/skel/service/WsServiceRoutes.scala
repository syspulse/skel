package io.syspulse.skel.service

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.service.ws.WsRoutes
import io.syspulse.skel.service.ws.WebSocket
import io.syspulse.skel.service.ws.WebSocketEcho

class WsServiceRoutes()(implicit context: ActorContext[_]) extends WsRoutes("ws")(context) {
  val ws0 = new WebSocketEcho()
  override def ws:WebSocket = ws0

  def broadcast(txt:String,topic:String="") = ws0.broadcastText(txt,topic)

}
