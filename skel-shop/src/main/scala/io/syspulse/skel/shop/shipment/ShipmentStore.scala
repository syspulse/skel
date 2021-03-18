package io.syspulse.skel.shop.shipment

import scala.util.{Try,Success}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ShipmentStore extends Store[Shipment] {
  
  def +(shipment:Shipment):Try[ShipmentStore]
  def -(shipment:Shipment):Try[ShipmentStore]
  def -(id:UUID):Try[ShipmentStore]
  def get(id:UUID):Option[Shipment]
  def getByOrderId(oid:UUID):Option[Shipment]
  def getAll:Seq[Shipment]
  def size:Long

  def load:Seq[Shipment]
  def clear:Try[ShipmentStore]

  def ++(shipments:Seq[Shipment],delay:Long = 0L):Try[ShipmentStore] = {
    shipments.foreach { 
      i => this.+(i)
      if(delay>0L) Thread.sleep(delay)
    }
    Success(this)
  }
}

