package io.syspulse.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import io.syspulse.skel.store.Store

import io.syspulse.dash.server._

import io.syspulse.dash.Dash
import java.time.LocalDateTime

trait DashStore extends Store[Dash,String] {
  private val log = Logger(getClass)

  def getMaxContextSize(): Int = 1024 * 1024 * 1 // 1M
  def getMaxChats(): Int = 10

  def getKey(d: Dash): String = d.id
  def +(d:Dash):Try[Dash]
  //def del(id:String):Try[String]
  
  def ??(id:String):Option[Dash]

  def ???(id:String,tid:Option[String],pid:Option[String]): Try[Dash]
  
  def ?(id:String):Try[Dash] = {
    ??(id) match {
      case Some(data) => Success(data)
      case None => Failure(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:String,tid:Option[String],pid:Option[String]): Try[String]

  def all:Seq[Dash] = all(None,None) 
  def all(tid:Option[String],pid:Option[String]):Seq[Dash]

  def size:Long
  def size(tid:Option[String],pid:Option[String]):Long

  
}
