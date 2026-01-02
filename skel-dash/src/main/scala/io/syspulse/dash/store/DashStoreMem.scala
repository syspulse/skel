package io.syspulse.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import io.jvm.uuid.UUID
import com.typesafe.scalalogging.Logger

import io.syspulse.dash.server._
import java.time.LocalDateTime
import io.syspulse.dash.Dash

class DashStoreMem() extends DashStore {

  var chats:Map[String,Dash] = Map()
  
  def +(u:Dash):Try[Dash] = {
    chats = chats + (getKey(u) -> u)
    Success(u)
  }

  def del(id:String):Try[String] = {
    chats = chats - id
    Success(id)
  }

  def ??(id:String):Option[Dash] = chats.get(id)

  def ???(id:String,tid:Option[String],pid:Option[String]): Try[Dash] = {
    chats.get(id) match {
      case Some(data) if(pid.isDefined && tid.isDefined && data.pid == pid && data.tid == tid) => Success(data)
      case Some(data) if(pid.isDefined && data.pid == pid) => Success(data)
      case Some(data) if(tid.isDefined && data.tid == tid) => Success(data)
      case Some(data) => Success(data)
      case None => Failure(new Exception(s"dash not found: ${tid}/${pid}/${id}"))
    }
  }

  def del(cid:String,tid:Option[String],pid:Option[String]): Try[String] = {
    this.???(cid,tid,pid) match {
      case Success(data) => 
        chats = chats - cid
        Success(cid)
      case Failure(e) => Failure(e)
    }
  }
    
  def all(tid:Option[String],pid:Option[String]):Seq[Dash] = 
    chats
      .values
      .filter(d => !pid.isDefined || d.pid == pid)
      .filter(d => !tid.isDefined || d.tid == tid)
      .toSeq
  
  def size:Long = chats.size
  def size(tid:Option[String],pid:Option[String]):Long = all(tid,pid).size


}
