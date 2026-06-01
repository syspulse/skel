package io.syspulse.skel.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.jvm.uuid.UUID
import com.typesafe.scalalogging.Logger

import java.time.LocalDateTime
import io.syspulse.skel.dash.Dash
import io.syspulse.skel.ErrNotFound

class DashStoreMem() extends DashStore {

  var chats:Map[String,Dash] = Map()

  def +(u:Dash):Future[Dash] = {
    chats = chats + (getKey(u) -> u)
    Future.successful(u)
  }

  override def del(id:String):Future[String] = {
    chats = chats - id
    Future.successful(id)
  }

  def ??(id:String):Future[Option[Dash]] = Future.successful(chats.get(id))

  def ???(id:String,tid:Option[String],pid:Option[String]): Future[Dash] = {
    chats.get(id) match {
      case Some(data) if(pid.isDefined && tid.isDefined && data.pid == pid && data.tid == tid) => Future.successful(data)
      case Some(data) if(pid.isDefined && data.pid == pid) => Future.successful(data)
      case Some(data) if(tid.isDefined && data.tid == tid) => Future.successful(data)
      case Some(data) => Future.successful(data)
      case None =>
        Future.failed(new ErrNotFound(s"Dash: ${tid}/${pid}/${id}"))
    }
  }

  def del(cid:String,tid:Option[String],pid:Option[String]): Future[String] = {
    this.???(cid,tid,pid).flatMap { _ =>
      chats = chats - cid
      Future.successful(cid)
    }
  }

  def all(tid:Option[String],pid:Option[String]):Future[Seq[Dash]] =
    Future.successful(
      chats
        .values
        .filter(d => !pid.isDefined || d.pid == pid)
        .filter(d => !tid.isDefined || d.tid == tid)
        .toSeq
    )

  def size:Future[Long] = Future.successful(chats.size.toLong)
  def size(tid:Option[String],pid:Option[String]):Future[Long] = all(tid,pid).map(_.size.toLong)


}
