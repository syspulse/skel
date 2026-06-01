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
import io.syspulse.skel.uri.RedisURI

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import spray.json._
import io.syspulse.skel.odometer.server.OdoJson

import scala.collection.mutable.ListBuffer


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

  // Use internal ActorSystem's dispatcher (execution context) for callbacks
  // Not implicit to avoid ambiguity with ?? method parameter
  private val redisEc: ExecutionContext = redis.dispatcher

  def all:Future[Seq[Odo]] = {
    implicit val ec = redisEc
    for {
      r1 <- redis.keys("*")
      r2 <- redis.mGet(r1.toSeq: _*)
    } yield r2.flatMap(_.map(v => v.parseJson.convertTo[Odo]))
  }

  def scan(pattern:String):Future[Seq[Odo]] = {
    implicit val ec = redisEc
    for {
      r1 <- {
        val keys = ListBuffer[String]()
        var cursor = 0L
        var done = false
        while (!done) {
          val f = redis.scan(cursor,Some(pattern))
          val (next, set) = Await.result(f,timeout)
          keys ++= set
          cursor = next
          done = (cursor == 0)
        }
        Future.successful(keys.toSeq)
      }
      r2 <- {
        if(r1.isEmpty)
          Future.successful(Seq[Option[String]]())
        else
          redis.mGet(r1.toSeq: _*)
      }
    } yield r2.flatMap(v => v.map(_.parseJson.convertTo[Odo]))
  }

  def size:Future[Long] = redis.dbSize()

  def +(o:Odo):Future[Odo] = {
    implicit val ec = redisEc
    log.debug(s"add: ${o}")
    redis.set(o.id,o.toJson.compactPrint).map(_ => o)
  }

  def del(id:String):Future[String] = {
    implicit val ec = redisEc
    log.info(s"del: ${id}")
    redis.del(id).flatMap { r =>
      if(r == 0) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
    }
  }

  def ?(id:String):Future[Odo] = {
    implicit val ec = redisEc
    redis.get(id).flatMap {
      case Some(o) => Future.successful(o.parseJson.convertTo[Odo])
      case None => Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def update(id:String,v:Long):Future[Odo] = {
    implicit val ec = redisEc
    this.?(id).flatMap { o =>
      val o1 = modify(o,v)
      this.+(o1).map(_ => o1)
    }
  }

  def ++(id:String, delta:Long):Future[Odo] = {
    implicit val ec = redisEc
    this.?(id).flatMap { o =>
      val o1 = o.copy(v = o.v + delta, ts = System.currentTimeMillis)
      this.+(o1).map(_ => o1)
    }
  }

  def clear():Future[OdoStore] = {
    implicit val ec = redisEc
    log.info("clear: ")
    redis.flushDB().map(_ => this)
  }

  override def ??(ids:Seq[String])(implicit ec:scala.concurrent.ExecutionContext):Future[Seq[Odo]] = {
    Future.traverse(ids) { id =>
      id.split(":").toList match {
        case ns :: "*" :: Nil =>
          scan(s"${ns}:*")

        case "*" :: Nil =>
          // use optimized scan
          scan("*")

        case _ =>
          ?(id).map(o => Seq(o)).recover { case _ => Seq() }
      }
    }.map(_.flatten)
  }
}
