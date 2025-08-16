package io.syspulse.skel.coingecko

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{ExecutionContext,Future,Await}

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import spray.json._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.CoingeckoURI
import io.syspulse.skel.blockchain.{Token,Coin,TokenProviderCoinGecko}

case class Coingecko_Coin(id:String,symbol:String,name:String)
case class Coingecko_CoinsList(coins:Seq[Coingecko_Coin])

object CoingeckoJson extends DefaultJsonProtocol {
  implicit val js_Token = jsonFormat6(Token)
  implicit val js_Coin = jsonFormat7(Coin)
  implicit val js_cg_Coin = jsonFormat3(Coingecko_Coin)
  implicit val js_cg_CoinsList = jsonFormat1(Coingecko_CoinsList)
}

class Coingecko(uri:String)(implicit ec: ExecutionContext) extends CoingeckoClient {
  import CoingeckoJson._

  val cgUri = CoingeckoURI(uri)
  val tokenProvider = new TokenProviderCoinGecko(Some(cgUri.apiKey))
  
  def getUri() = cgUri

  def coins(ids:Set[String])(implicit ec: ExecutionContext):Future[Seq[JsValue]] = {
    val req = requestCoins(getUri(),ids,cgUri.apiKey)

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

  def flowCoin = Flow[ByteString]
    .map{ case(body) => {
      log.debug(s"body='${body}'")
      tokenProvider.decodeCoin(body.utf8String)      
    }}
    .collect{ case Success(c) => c.toJson }
  
  def flowCoinList = Flow[ByteString]
    .map{ case(body) => {
      log.debug(s"body='${body}'")
      // extract ids
      val cc = body.utf8String.parseJson.convertTo[Coingecko_CoinsList]
      cc.coins.map(_.id)
    }}
    .mapConcat(identity)
    .throttle(1,FiniteDuration(cgUri.throttle,TimeUnit.MILLISECONDS))
    .mapAsync(1)(id => {
      requestCoins(getUri(),Set(id),cgUri.apiKey)
    })    
    .via(flowCoin)

  def source(ids:Set[String],
             freq:Long = 10000L,  
             frameDelimiter:String = "\n",frameSize:Int = 1024 * 1024):Source[ByteString,_] = {

    // Frequency source
    val s0 = Source
      .tick(FiniteDuration(250L,TimeUnit.MILLISECONDS),FiniteDuration(freq,TimeUnit.MILLISECONDS),() => ids)
      .mapAsync(1)(fun => {
        val ids = fun()
        requestCoins(getUri(),ids,cgUri.apiKey)
      })
      .via(flowCoin)
      .map(json => ByteString(json.toString))

    s0    
  }
}

object Coingecko {

  def fromCoingecko(uri:String)(implicit ec: ExecutionContext) = {
    val cg = new Coingecko(uri)(ec)
    cg.source(cg.cgUri.ops.get("ids").map(_.split(",").toSet).getOrElse(Set()))
  }

  def apply(uri:String)(implicit ec: ExecutionContext):Try[Coingecko] = {
    try {
      val client = new Coingecko(uri)(ec)
      Success(client)

    } catch {
      case e:Exception => return Failure(e)
    }
  }
}