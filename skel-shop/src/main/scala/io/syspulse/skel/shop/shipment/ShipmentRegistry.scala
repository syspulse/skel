package io.syspulse.skel.shop.shipment

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import java.time._

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName
import java.time.ZonedDateTime


final case class Shipments(shipments: immutable.Seq[Shipment])

final case class ShipmentCreate(orderId:UUID, warehouseId:UUID, address:String, shipmentType:String)

object ShipmentRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetShipments(replyTo: ActorRef[Shipments]) extends Command
  final case class GetShipment(id:UUID,replyTo: ActorRef[GetShipmentResponse]) extends Command
  final case class GetShipmentByOrderId(oid:UUID,replyTo: ActorRef[GetShipmentResponse]) extends Command
  
  final case class CreateShipment(shipmentCreate: ShipmentCreate, replyTo: ActorRef[ShipmentActionPerformed]) extends Command
  final case class DeleteShipment(id: UUID, replyTo: ActorRef[ShipmentActionPerformed]) extends Command

  final case class GetShipmentResponse(shipment: Option[Shipment])
  final case class ShipmentActionPerformed(description: String,id:Option[UUID])
  final case class ClearActionPerformed(description: String,size:Long)

  final case class LoadShipments(replyTo: ActorRef[Shipments]) extends Command
  final case class ClearShipments(replyTo: ActorRef[ClearActionPerformed]) extends Command

  final case class GetRandomShipments(count: Long, replyTo: ActorRef[Shipments]) extends Command
  final case class CreateRandomShipments(count: Long, delay:Long, replyTo: ActorRef[Shipments]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: ShipmentStore = null

  def apply(store: ShipmentStore = new ShipmentStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("shipment-count") { store.size }

  private def registry(store: ShipmentStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetShipments(replyTo) =>
        replyTo ! Shipments(store.getAll)
        Behaviors.same
      case CreateShipment(shipmentCreate, replyTo) =>
        val id = UUID.randomUUID()
        val ts = ZonedDateTime.now()
        val shipment = Shipment(
          id,ts, shipmentCreate.orderId, shipmentCreate.warehouseId, 
          shipmentCreate.address, shipmentCreate.shipmentType
        )
        val store1 = store.+(shipment)
        replyTo ! ShipmentActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
        case GetShipment(id, replyTo) =>
        replyTo ! GetShipmentResponse(store.get(id))
        Behaviors.same

      case GetShipmentByOrderId(oid, replyTo) =>
        replyTo ! GetShipmentResponse(store.getByOrderId(oid))
        Behaviors.same

      case DeleteShipment(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! ShipmentActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case LoadShipments(replyTo) =>
        replyTo ! Shipments(store.load)
        Behaviors.same

      case GetRandomShipments(count,replyTo) =>
        val shipments = ShipmentGenerator.random(count)
        //val store1 = store.++(shipments)
        replyTo ! Shipments(shipments)
        Behaviors.same

      case CreateRandomShipments(count,delay,replyTo) =>
        val shipments = ShipmentGenerator.random(count)
        val store1 = store.++(shipments,delay)
        replyTo ! Shipments(shipments)
        registry(store1.getOrElse(store))

      case ClearShipments(replyTo) =>
        val size = store.size
        val store1 = store.clear
        replyTo ! ClearActionPerformed(s"cleared",size)
        registry(store1.getOrElse(store))
    }
  }
}
