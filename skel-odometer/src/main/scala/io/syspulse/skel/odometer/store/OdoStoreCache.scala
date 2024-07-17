package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.cron.CronFreq

class OdoStoreCache(store:OdoStore,freq:Long = 3000L) extends OdoStore {
  val log = Logger(s"${this}")

  val cache = new OdoStoreMem()
  val dirty = new OdoStoreMem()
  var cachedAll = false

  val cron = new CronFreq((_) => {
      if(dirty.size > 0) log.info(s"Flushing cache: ${dirty.size}")

      dirty.all.foreach{ o => {
        log.debug(s"Flushing: ${o}")
        store.update(o.id,o.v)
      }}
      // clear dirty cache
      dirty.clear()
      true
    },
    freq.toString,//FiniteDuration(freq,TimeUnit.MILLISECONDS),
    //freq
  )

  // always request everything and cache
  def all:Seq[Odo] = {
    val oo = store.all
    for( o <- oo ) {
      cache.+(o)
    }
    cachedAll = true
    oo
  }

  def size:Long = cache.size

  def +(o:Odo):Try[Odo] = { 
    for {
      r1 <- store.+(o)
      r2 <- cache.+(o)      
    } yield o
  }

  def del(id:String):Try[String] = {
    // optimistic delete dirty
    dirty.del(id)

    for {
      r1 <- store.del(id)
      r2 <- cache.del(id)
    } yield id
  }

  def ????(id:String) = {
    cache.?(id) match {
      case Success(o) => Success(o)
      case _ => 
        // try to get from store
        store.?(id).map(o => {
          cache.+(o)          
          o
        })
    }
  }

  def ?(id:String):Try[Odo] = {
    ????(id)
  }

  def update(id:String,v:Long):Try[Odo] = {
    val o = ????(id)
    for {
      o <- o
      o1 <- cache.update(id,v)
      _ <-  dirty.+(o1)
    } yield o1
  }

  def ++(id:String,delta:Long):Try[Odo] = {
    val o = ????(id)    
    for {
      o <- o
      o1 <- cache.++(o.id,delta)
      _ <- dirty.+(o1)
    } yield o1
  }

  def clear():Try[OdoStore] = {
    for {
      r1 <- store.clear()
      r2 <- cache.clear()
      _ <- dirty.clear()
    } yield this
  }

  override def ??(ids:Seq[String]):Seq[Odo] = {
    // val oo = cache.??(ids) 
    // if(oo.size == 0) {
    //   // try to get from store
    //   val oo = store.??(ids)
    //   for( o <- oo) {
    //     cache.+(o)
    //   }
    //   oo
    // } else oo

    val oo = ids.flatMap( id => {
      id.split(":").toList match {
        case ns :: "*" :: Nil =>           
          val oo = cache.??(Seq(id))
          
          val oo1 = if(oo.size == 0) {
            store.??(Seq(id))
          } else oo

          for( o <- oo1) {
            cache.+(o)
          }
          oo1
        
        case "*" :: Nil => 
          if( !cachedAll ) {
            val oo = store.??(Seq("*"))
            if(oo.size != 0)
              cachedAll = true
            oo
          }
          else
            cache.??(Seq("*"))

        case _ => 
          this.?(id) match {
            case Success(o) => Seq(o)
            case _ => Seq()
          }        
      }
    })
    oo
  }

  
  // start cron
  cron.start()
}
