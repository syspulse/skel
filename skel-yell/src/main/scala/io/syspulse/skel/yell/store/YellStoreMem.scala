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

class YellStoreMem extends YellStore {
  val log = Logger(s"${this}")
  
  var yells: Map[ID,Yell] = Map()

  def all:Seq[Yell] = yells.values.toSeq

  def size:Long = yells.size

  def +(yell:Yell):Try[YellStore] = { 
    yells = yells + (Yell.uid(yell) -> yell)
    log.info(s"${yell}")
    Success(this)
  }

  def del(id:ID):Try[YellStore] = { 
    val sz = yells.size
    yells = yells - id;
    log.info(s"${id}")
    if(sz == yells.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def -(yell:Yell):Try[YellStore] = {     
    del(Yell.uid(yell))
  }

  def ?(id:ID):Option[Yell] = yells.get(id)

  def ??(txt:String):List[Yell] = {
    yells.values.filter(y => 
      y.area.matches(txt) || 
      y.text.matches(txt)
    ).toList
  }

  def scan(txt:String):List[Yell] = ??(txt)
  def search(txt:String):List[Yell] = ??(txt)
  def grep(txt:String):List[Yell] = ??(txt)

}
