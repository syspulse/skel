package io.syspulse.skel.service.ws

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import akka.util.Timeout

import akka.NotUsed
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.actor.ActorRef
import akka.stream.Materializer
import scala.concurrent.ExecutionContext


class WebSocketPassthrough()(implicit ex:ExecutionContext, mat:ActorMaterializer) extends WebSocket() {
  override def process(m:Message,a:ActorRef):Message = m
}
