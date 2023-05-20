package io.syspulse.skel.service.ws

import scala.collection.mutable

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
import akka.stream.Materializer
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit


abstract class WebSocket(idleTimeout:Long)(implicit ex:ExecutionContext) {
  val log = Logger(s"${this}")

  @volatile
  protected var clients: mutable.Map[String,mutable.ListBuffer[ActorRef]] = mutable.Map()

  def all() = clients.keys

  def -(topic:String,wsActor:ActorRef) = {
    val aa = clients.get(topic).getOrElse(mutable.ListBuffer())
    aa.synchronized {
      aa.-=(wsActor)
      if(aa.size == 0) {
        clients.remove(topic)
      }
    }
    log.info(s"clients: ${clients}")
  }
  
  def +(topic:String,wsActor:ActorRef) = {
    val aa = clients.getOrElseUpdate(topic,mutable.ListBuffer())
    aa.synchronized {
      aa.addOne(wsActor)      
    }
    log.info(s"clients: ${clients}")
  }
  
  def process(m:Message,a:ActorRef):Message = ???

  def wsFlow(topic:String)(implicit mat:Materializer): Flow[Message, Message, Any] = {    
    val (wsActor, wsSource) = 
      Source
        .actorRef[Message](32, OverflowStrategy.dropNew)
        .preMaterialize()

    this.+(topic,wsActor)
    
    // it must be coupled to detect WS client disconnects!
    val flow = Flow.fromSinkAndSourceCoupled(
      Sink.foreach{ m:Message =>         
        process(m,wsActor)
      },
      wsSource
        .map(m => {
          log.debug(s"connection=${wsActor}: ${m}")
          m
        })
        //.idleTimeout(FiniteDuration(idleTimeout,TimeUnit.MILLISECONDS))
    )
    .idleTimeout(FiniteDuration(idleTimeout,TimeUnit.MILLISECONDS))
    .watchTermination()( (prevMatValue, f) => {
      // this function will be run when the stream terminates
      // the Future provided as a second parameter indicates whether the stream completed successfully or failed
      f.onComplete {
        case Failure(e) => {
          log.error(s"connection: ${wsActor}",e)
          this.-(topic,wsActor)          
        }
        case Success(_) => {
          this.-(topic,wsActor)          
        }
      }
    })

    flow
  }

  def listen(topic:String="")(implicit mat:Materializer): Flow[Message, Message, Any] = {
    wsFlow(topic)
  }

  def broadcastText(text: String,topic:String=""): Unit = {
    log.info(s"broadcasting: '${text}' -> ${clients}")
    for (aa <- clients.get(topic)) 
      aa.map(_ ! TextMessage.Strict(text))
  }

  def sendText(actor:ActorRef, text: String): Unit = {
    clients.values.flatten.filter(_.toString == actor).foreach{ a => a ! TextMessage.Strict(text) }
  }
}
