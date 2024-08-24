package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.odometer.Odo

import scredis.Redis
import scredis.Client
import scredis.protocol.AuthConfig

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await

import spray.json._
import io.syspulse.skel.odometer.server.OdoJson
import io.syspulse.skel.uri.RedisURI
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


class OdoStoreRedis(uri:String,redisTimeout:Long = 3000L) extends OdoStore {
  val log = Logger(s"${this}")

  import OdoJson._

  val timeout = FiniteDuration(redisTimeout,TimeUnit.MILLISECONDS)

  val redisUri = RedisURI(uri)
    
  val redis = Redis(
    host = redisUri.host,
    port = redisUri.port,
    authOpt = redisUri.pass match {
      case None => None
      case Some(u) => Some(AuthConfig(username = redisUri.user, password = redisUri.pass.getOrElse("")))
    },
    database = redisUri.db,
    connectTimeout = timeout
  )

  // Import internal ActorSystem's dispatcher (execution context) to register callbacks
  import redis.dispatcher
  

  def all:Seq[Odo] = {
    val f = for {
      r1 <-  redis.keys("*")       
      r2 <- {
        redis.mGet(r1.toSeq: _*)
      }
    } yield r2

    val r = Await.result(f,timeout)          
    val oo = r.flatMap(_.map(v => v.parseJson.convertTo[Odo]))
    oo
  }

  def scan(pattern:String):Seq[Odo] = {
    val f = for {
      r1 <- {
        val keys = ListBuffer[String]()
        var cursor = 0L
        do {                
          val f = redis.scan(cursor,Some(pattern))
          val (next, set) = Await.result(f,timeout)
          keys ++= set
          cursor = next
        } while (cursor > 0)
        Future(keys)
      }
      r2 <- {
        if(r1.size == 0)
          Future(Seq())
        else
          redis.mGet(r1.toSeq: _*)
      }
    } yield r2

    val r = Await.result(f,timeout)          
    val oo = r.flatMap(v => v.map(_.parseJson.convertTo[Odo]))
    oo      
  }

  def size:Long = {
    val f = redis.dbSize()
    Await.result(f,timeout)
  }

  def +(o:Odo):Try[Odo] = { 
    val f = redis.set(o.id,o.toJson.compactPrint)
    log.debug(s"add: ${o}")
    Success(o)
  }

  def del(id:String):Try[String] = { 
    log.info(s"del: ${id}")
    val f = redis.del(id)
    val r = Await.result(f,timeout)
    if(r == 0) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  def ?(id:String):Try[Odo] = {
    val f = redis.get(id)
    Await.result(f,timeout) match {    
      case Some(o) => Success(o.parseJson.convertTo[Odo])
      case None => Failure(new Exception(s"not found: ${id}"))
    }
  }

  def update(id:String,v:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = modify(o,v)
        this.+(o1)
        Success(o1)
      case f => f
    }
  }

  def ++(id:String, delta:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = o.copy(v = o.v + delta, ts = System.currentTimeMillis)
        this.+(o1)        
        Success(o1)
      case f => f
    }
  }

  def clear():Try[OdoStore] = {
    log.info("clear: ")
    val f = redis.flushDB()
    Await.result(f,timeout)
    Success(this)
  }

  override def ??(ids:Seq[String]):Seq[Odo] = {    
    val oo = ids.flatMap( id => {

      id.split(":").toList match {        
        case ns :: "*" :: Nil => 
          scan(s"${ns}:*")

        case "*" :: Nil => 
          // use optimized scan
          scan("*")
        
        case _ =>
          ?(id) match {
            case Success(o) => Seq(o)
            case _ => Seq()
          }
      }      
    })
    oo    
  }
}
