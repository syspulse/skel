package io.syspulse.skel.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.dash.server.DashJson._
import io.syspulse.skel.dash.Dash

class DashStoreDir(dir:String = "store") extends StoreDir[Dash,String](dir) with DashStore {
  override val log = Logger(getClass)

  val store = new DashStoreMem()

  def toKey(id:String):String = id
  def all(tid:Option[String],oid:Option[String]):Future[Seq[Dash]] = store.all(tid,oid)
  def size:Future[Long] = store.size
  def size(tid:Option[String],oid:Option[String]):Future[Long] = store.size(tid,oid)
  override def +(u:Dash):Future[Dash] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:String):Future[String] = super.del(id).flatMap(_ => store.del(id))
  override def ??(id:String):Future[Option[Dash]] = store.??(id)
  override def ???(cid:String,tid:Option[String],oid:Option[String]): Future[Dash] = store.???(cid,tid,oid)

  def del(cid:String,tid:Option[String],oid:Option[String]): Future[String] = {
    store.del(cid,tid,oid).flatMap { res =>
      super[StoreDir].del(cid).map(_ => res)
    }
  }

  // load directory
  load(dir)
}
