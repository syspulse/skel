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
  
  val cache = new OdoStoreMem()

  val cron = new CronFreq(() => {
      all.foreach{ o => {
        store.update(o.id,o.counter)
      }}

      store.clear()

      true
    },
    FiniteDuration(freq,TimeUnit.MILLISECONDS),
    freq
  )

  def all:Seq[Odo] = cache.all

  def size:Long = cache.size

  def +(o:Odo):Try[OdoStore] = { 
    for {
      r1 <- store.+(o)
      r2 <- cache.+(o)
    } yield this
  }

  def del(id:String):Try[OdoStore] = { 
    for {
      r1 <- store.del(id)
      r2 <- cache.del(id)
    } yield this
  }

  def ?(id:String):Try[Odo] = cache.?(id)

  def update(id:String,delta:Long):Try[Odo] = {
    for {
      r1 <- cache.update(id,delta)
    } yield r1
  }

  def clear():Try[OdoStore] = {
    for {
      r1 <- store.clear()
      r2 <- cache.clear()
    } yield this
  }
  

  // start updater
  def start() = cron.start()
}
