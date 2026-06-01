package io.syspulse.skel.odometer.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.odometer._
import io.syspulse.skel.store.Store

import io.syspulse.skel.odometer.Odo

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait OdoStore extends Store[Odo,String] {

  def getKey(e: Odo): String = e.id
  def +(odometer:Odo):Future[Odo]

  def del(id:String):Future[String]
  def ?(id:String):Future[Odo]
  def all:Future[Seq[Odo]]
  def size:Future[Long]

  def update(id:String, v:Long):Future[Odo]

  def ++(id:String, delta:Long):Future[Odo]

  protected def modify(o:Odo,v:Long):Odo = {
    (for {
      o1 <- Some(o.copy(ts = System.currentTimeMillis,v = v))
    } yield o1).get
  }

  // this operator supports namespace "namespace:key"
  // Implementation should overwrite it if support fast version (like Redis)
  override def ??(ids:Seq[String])(implicit ec:ExecutionContext):Future[Seq[Odo]] = {
    if(ids.filter(_.contains(":*")).size != 0)
      Future.failed(new Exception(s"no implementation"))
    else
      super.??(ids)
  }
}
