package io.syspulse.skel.service

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.service.ws.WsRoutes
import io.syspulse.skel.service.ws.WebSocket
import io.syspulse.skel.service.ws.WebSocketEcho

class WsServiceRoutes()(implicit context: ActorContext[_]) extends WsRoutes("ws")(context) {
  override def ws:WebSocket = new WebSocketEcho()
}
