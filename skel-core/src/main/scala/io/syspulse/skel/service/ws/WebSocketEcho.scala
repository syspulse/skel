package io.syspulse.skel.service.ws

import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import akka.util.Timeout
import akka.NotUsed
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.util.{Success,Failure}
import akka.stream.Materializer


class WebSocketEcho(idleTimeout:Long = 1000L*60*5)(implicit ex:ExecutionContext,mat:ActorMaterializer) extends WebSocket(idleTimeout) {
  override def process(m:Message,a:ActorRef):Message = {
    val txt = m.asTextMessage.getStrictText
    a ! m
    m
  }
}
