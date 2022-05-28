package io.syspulse.skel.shop.warehouse

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class WarehouseStoreMem extends WarehouseStore {
  val log = Logger(s"${this}")
  
  var warehouses: Set[Warehouse] = Set()

  def getAll:Seq[Warehouse] = warehouses.toSeq
  def size:Long = warehouses.size

  def +(warehouse:Warehouse):Try[WarehouseStore] = { warehouses = warehouses + warehouse; Success(this)}
  def -(id:UUID):Try[WarehouseStore] = { 
    warehouses.find(_.id == id) match {
      case Some(warehouse) => { warehouses = warehouses - warehouse; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(warehouse:Warehouse):Try[WarehouseStore] = { 
    val sz = warehouses.size
    warehouses = warehouses - warehouse;
    if(sz == warehouses.size) Failure(new Exception(s"not found: ${warehouse}")) else Success(this)
  }

  def get(id:UUID):Option[Warehouse] = warehouses.find(_.id == id)

  def getByName(name:String):Option[Warehouse] = warehouses.find( c => 
    c.name.compareToIgnoreCase(name) == 0
  )

  def load:Seq[Warehouse] = {
    warehouses = WarehouseLoader.fromResource().toSet
    getAll
  }

  def clear:Try[WarehouseStore] = {
    warehouses = Set()
    Success(this)
  }
}
