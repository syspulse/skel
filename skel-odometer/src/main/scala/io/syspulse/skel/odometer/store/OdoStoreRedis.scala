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
    val f = redis.scan(0L,Some("*"))
    val r = Await.result(f,timeout)
    r._2.map(v => v.parseJson.convertTo[Odo]).toSeq
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

  def update(id:String,counter:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = modify(o,counter)
        this.+(o1)
        Success(o1)
      case f => f
    }
  }

  def ++(id:String, delta:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = o.copy(counter = o.counter + delta, ts = System.currentTimeMillis)
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
}
