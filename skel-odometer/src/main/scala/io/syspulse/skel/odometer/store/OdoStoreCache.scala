package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.cron.CronFreq
import io.syspulse.skel.store.Store

class OdoStoreCache(store:OdoStore,freq:Long = 3000L) extends OdoStore {
  val log = Logger(s"${this}")

  private implicit val ec: ExecutionContext = ExecutionContext.global

  val cache = new OdoStoreMem()
  val dirty = new OdoStoreMem()
  var cachedAll = false

  val cron = new CronFreq((_) => {
      Store.fromFuture(dirty.size).foreach { sz =>
        if(sz > 0) log.info(s"Flushing cache: ${sz}")
      }

      Store.fromFuture(dirty.all).foreach { oo =>
        oo.foreach { o =>
          log.debug(s"Flushing: ${o}")
          store.update(o.id,o.v)
        }
      }
      // clear dirty cache
      dirty.clear()
      true
    },
    freq.toString,//FiniteDuration(freq,TimeUnit.MILLISECONDS),
    //freq
  )

  // always request everything and cache
  def all:Future[Seq[Odo]] = {
    store.all.map { oo =>
      for( o <- oo ) {
        cache.+(o)
      }
      cachedAll = true
      oo
    }
  }

  def size:Future[Long] = cache.size

  def +(o:Odo):Future[Odo] = {
    for {
      r1 <- store.+(o)
      r2 <- cache.+(o)
    } yield o
  }

  def del(id:String):Future[String] = {
    // optimistic delete dirty
    dirty.del(id)

    for {
      r1 <- store.del(id)
      r2 <- cache.del(id)
    } yield id
  }

  private def ????(id:String):Future[Odo] = {
    cache.?(id).recoverWith { case _ =>
      // try to get from store
      store.?(id).flatMap { o =>
        cache.+(o).map(_ => o)
      }
    }
  }

  def ?(id:String):Future[Odo] = {
    ????(id)
  }

  def update(id:String,v:Long):Future[Odo] = {
    for {
      o  <- ????(id)
      o1 <- cache.update(id,v)
      _  <- dirty.+(o1)
    } yield o1
  }

  def ++(id:String,delta:Long):Future[Odo] = {
    for {
      o  <- ????(id)
      o1 <- cache.++(o.id,delta)
      _  <- dirty.+(o1)
    } yield o1
  }

  def clear():Future[OdoStore] = {
    for {
      r1 <- cache.clear()
      _  <- dirty.clear()
    } yield this
  }

  override def ??(ids:Seq[String])(implicit ec:scala.concurrent.ExecutionContext):Future[Seq[Odo]] = {
    Future.traverse(ids) { id =>
      id.split(":").toList match {
        case ns :: "*" :: Nil =>
          cache.??(Seq(id)).flatMap { oo =>
            if(oo.isEmpty) {
              store.??(Seq(id)).flatMap { oo1 =>
                Future.traverse(oo1)(o => cache.+(o)).map(_ => oo1)
              }
            } else Future.successful(oo)
          }

        case "*" :: Nil =>
          if( !cachedAll ) {
            store.??(Seq("*")).map { oo =>
              if(oo.nonEmpty) cachedAll = true
              oo
            }
          } else
            cache.??(Seq("*"))

        case _ =>
          this.?(id).map(o => Seq(o)).recover { case _ => Seq() }
      }
    }.map(_.flatten)
  }


  // start cron
  cron.start()
}
