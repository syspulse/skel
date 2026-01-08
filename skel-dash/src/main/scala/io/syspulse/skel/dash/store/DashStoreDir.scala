package io.syspulse.skel.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.dash.server.DashJson._
import io.syspulse.skel.dash.Dash

class DashStoreDir(dir:String = "store") extends StoreDir[Dash,String](dir) with DashStore {
  override val log = Logger(getClass)

  val store = new DashStoreMem()

  def toKey(id:String):String = id
  def all(tid:Option[String],oid:Option[String]):Seq[Dash] = store.all(tid,oid)
  def size:Long = store.size
  def size(tid:Option[String],oid:Option[String]):Long = store.size(tid,oid)
  override def +(u:Dash):Try[Dash] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:String):Try[String] = super.del(id).flatMap(_ => store.del(id))  
  override def ??(id:String):Option[Dash] = store.??(id)
  override def ???(cid:String,tid:Option[String],oid:Option[String]): Try[Dash] = store.???(cid,tid,oid)
    

  def del(cid:String,tid:Option[String],oid:Option[String]): Try[String] = {
    store.del(cid,tid,oid) match {
      case Success(res) => 
        super.del(cid)
        Success(res)
      case Failure(e) => Failure(e)
    }
  }

  // load directory
  load(dir)
}
