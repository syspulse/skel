package io.syspulse.skel.shop.item

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ItemStoreMem extends ItemStore {
  val log = Logger(s"${this}")
  
  var items: Set[Item] = Set()

  def getAll:Seq[Item] = items.toSeq
  def size:Long = items.size

  def +(item:Item):Try[ItemStore] = { items = items + item; Success(this)}
  def -(id:UUID):Try[ItemStore] = { 
    items.find(_.id == id) match {
      case Some(item) => { items = items - item; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(item:Item):Try[ItemStore] = { 
    val sz = items.size
    items = items - item;
    if(sz == items.size) Failure(new Exception(s"not found: ${item}")) else Success(this)
  }

  def get(id:UUID):Option[Item] = items.find(_.id == id)

  def getByName(name:String):Option[Item] = items.find( c => 
    c.name.compareToIgnoreCase(name) == 0
  )

  def load:Seq[Item] = {
    items = ItemLoader.fromResource().toSet
    getAll
  }

  def clear:Try[ItemStore] = {
    items = Set()
    Success(this)
  }
}
