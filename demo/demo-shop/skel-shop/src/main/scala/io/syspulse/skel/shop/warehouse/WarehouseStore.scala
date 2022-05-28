package io.syspulse.skel.shop.warehouse

import scala.util.{Try,Success}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait WarehouseStore extends Store[Warehouse] {
  
  def +(warehouse:Warehouse):Try[WarehouseStore]
  def -(warehouse:Warehouse):Try[WarehouseStore]
  def -(id:UUID):Try[WarehouseStore]
  def get(id:UUID):Option[Warehouse]
  def getByName(name:String):Option[Warehouse]
  def getAll:Seq[Warehouse]
  def size:Long

  def load:Seq[Warehouse]
  def clear:Try[WarehouseStore]

  def ++(warehouses:Seq[Warehouse],delay:Long = 0L):Try[WarehouseStore] = {
    warehouses.foreach { 
      i => this.+(i)
      if(delay>0L) Thread.sleep(delay)
    }
    Success(this)
  }
}

