package io.syspulse.skel.shop.shipment

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ShipmentStoreMem extends ShipmentStore {
  val log = Logger(s"${this}")
  
  var shipments: Set[Shipment] = Set()

  def getAll:Seq[Shipment] = shipments.toSeq
  def size:Long = shipments.size

  def +(shipment:Shipment):Try[ShipmentStore] = { shipments = shipments + shipment; Success(this)}
  def -(id:UUID):Try[ShipmentStore] = { 
    shipments.find(_.id == id) match {
      case Some(shipment) => { shipments = shipments - shipment; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(shipment:Shipment):Try[ShipmentStore] = { 
    val sz = shipments.size
    shipments = shipments - shipment;
    if(sz == shipments.size) Failure(new Exception(s"not found: ${shipment}")) else Success(this)
  }

  def get(id:UUID):Option[Shipment] = shipments.find(_.id == id)

  def getByOrderId(oid:UUID):Option[Shipment] = shipments.find( c => 
    c.orderId == oid
  )

  def load:Seq[Shipment] = {
    shipments = ShipmentLoader.fromResource().toSet
    getAll
  }

  def clear:Try[ShipmentStore] = {
    shipments = Set()
    Success(this)
  }
}
