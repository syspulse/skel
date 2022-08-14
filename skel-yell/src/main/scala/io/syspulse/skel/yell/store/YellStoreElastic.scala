package io.syspulse.skel.yell.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.yell._
import io.syspulse.skel.yell.Yell.ID

import io.syspulse.skel.yell.YellScan

class YellStoreElastic extends YellScan with YellSearch with YellStore {
  
  import io.syspulse.skel.yell.elastic.YellElasticJson
  import io.syspulse.skel.yell.elastic.YellElasticJson._
  override implicit val fmt = YellElasticJson.fmt
  
  def all:Seq[Yell] = scan("")

  // slow and memory hungry !
  def size:Long = scan("").size

  def +(yell:Yell):Try[YellStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def del(id:ID):Try[YellStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def -(yell:Yell):Try[YellStore] = {     
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def ?(id:ID):Option[Yell] = searches(id.split("_").mkString(" ")).headOption

  def ??(txt:String):List[Yell] = {
    searches(txt).toList
  }

  override def connect(config:Config):YellStore = {
    connect(config.elasticUri, config.elasticIndex)
    this
  }

  override def scan(txt:String):List[Yell] = super.scan(txt).toList
  override def search(txt:String):List[Yell] = super.searches(txt).toList
  override def grep(txt:String):List[Yell] = super.grep(txt).toList
}
