package io.syspulse.skel.coingecko

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{ExecutionContext,Future,Await}

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import io.syspulse.skel.Ingestable
import io.syspulse.skel.coingecko.CoingeckoURI

import spray.json._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class Coingecko(uri:String) extends CoingeckoClient {
  
  val cgUri = CoingeckoURI(uri)
  
  def coins(ids:Set[String])(implicit ec: ExecutionContext):Future[Seq[JsValue]] = {
    val req = requestCoins(ids,cgUri.apiKey)

    req.map(body => {
      val json = body.utf8String.parseJson
      log.debug(s"${ids}: results=${json}")
      
      Seq(json)
    })
  }

  def askCoins(ids:Set[String])(implicit ec: ExecutionContext):Seq[JsValue] = {
    val f = coins(ids)
    Await.result(f,cgUri.timeout)
  }

  def askCoins()(implicit ec: ExecutionContext):Seq[JsValue] = {
    val f = coins(Set())
    Await.result(f,cgUri.timeout)
  }

  def flow = Flow[ByteString]
    .map{ case(body) => {
      log.debug(s"body='${body}'")
      body
    }}
  

  def source(ids:Set[String],
             freq:Long = 10000L,  
             frameDelimiter:String = "\n",frameSize:Int = 1024 * 1024)(implicit ec: ExecutionContext):Source[ByteString,_] = {

    // Frequency source
    val s0 = Source
      .tick(FiniteDuration(250L,TimeUnit.MILLISECONDS),FiniteDuration(freq,TimeUnit.MILLISECONDS),() => ids)
      .mapAsync(1)(fun => {
        val ids = fun()
        requestCoins(ids,cgUri.apiKey)
      })
      .via(flow)

    s0    
  }
}

object Coingecko {

  def fromCoingecko(uri:String)(implicit ec: ExecutionContext) = {
    val cg = new Coingecko(uri)
    cg.source(cg.cgUri.ops.get("ids").map(_.split(",").toSet).getOrElse(Set()))
  }

  def apply(uri:String):Try[Coingecko] = {
    try {
      val client = new Coingecko(uri)
      Success(client)

    } catch {
      case e:Exception => return Failure(e)
    }
  }
}