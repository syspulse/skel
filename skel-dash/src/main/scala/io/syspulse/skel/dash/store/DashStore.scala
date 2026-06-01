package io.syspulse.skel.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID
import java.time.LocalDateTime

import io.syspulse.skel.store.Store
import io.syspulse.skel.dash.Dash

trait DashStore extends Store[Dash,String] {
  private val log = Logger(getClass)

  def getMaxContextSize(): Int = 1024 * 1024 * 1 // 1M
  def getMaxChats(): Int = 10

  def getKey(d: Dash): String = d.id
  def +(d:Dash):Future[Dash]

  def ??(id:String):Future[Option[Dash]]

  def ???(id:String,tid:Option[String],pid:Option[String]): Future[Dash]

  def ?(id:String):Future[Dash] = {
    ??(id).flatMap {
      case Some(data) => Future.successful(data)
      case None => Future.failed(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:String):Future[String] = del(id,None,None)
  def del(id:String,tid:Option[String],pid:Option[String]): Future[String]

  def all:Future[Seq[Dash]] = all(None,None)
  def all(tid:Option[String],pid:Option[String]):Future[Seq[Dash]]

  def size:Future[Long]
  def size(tid:Option[String],pid:Option[String]):Future[Long]


}
