package io.syspulse.skel.shop.warehouse

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import java.time._

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName
import java.time.ZonedDateTime


final case class Warehouses(warehouses: immutable.Seq[Warehouse])

final case class WarehouseCreate(name:String, countryId:UUID, location:String)

object WarehouseRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetWarehouses(replyTo: ActorRef[Warehouses]) extends Command
  final case class GetWarehouse(id:UUID,replyTo: ActorRef[GetWarehouseResponse]) extends Command
  final case class GetWarehouseByName(name:String,replyTo: ActorRef[GetWarehouseResponse]) extends Command
  
  final case class CreateWarehouse(warehouseCreate: WarehouseCreate, replyTo: ActorRef[WarehouseActionPerformed]) extends Command
  final case class DeleteWarehouse(id: UUID, replyTo: ActorRef[WarehouseActionPerformed]) extends Command

  final case class GetWarehouseResponse(warehouse: Option[Warehouse])
  final case class WarehouseActionPerformed(description: String,id:Option[UUID])
  final case class ClearActionPerformed(description: String,size:Long)

  final case class LoadWarehouses(replyTo: ActorRef[Warehouses]) extends Command
  final case class ClearWarehouses(replyTo: ActorRef[ClearActionPerformed]) extends Command

  final case class GetRandomWarehouses(count: Long, replyTo: ActorRef[Warehouses]) extends Command
  final case class CreateRandomWarehouses(count: Long, delay:Long, replyTo: ActorRef[Warehouses]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: WarehouseStore = null

  def apply(store: WarehouseStore = new WarehouseStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("warehouse-count") { store.size }

  private def registry(store: WarehouseStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetWarehouses(replyTo) =>
        replyTo ! Warehouses(store.getAll)
        Behaviors.same
      case CreateWarehouse(warehouseCreate, replyTo) =>
        val id = UUID.randomUUID()
        val ts = ZonedDateTime.now()
        val warehouse = Warehouse(id,ts, warehouseCreate.name, warehouseCreate.countryId, warehouseCreate.location)
        val store1 = store.+(warehouse)
        replyTo ! WarehouseActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
        case GetWarehouse(id, replyTo) =>
        replyTo ! GetWarehouseResponse(store.get(id))
        Behaviors.same

      case GetWarehouseByName(name, replyTo) =>
        replyTo ! GetWarehouseResponse(store.getByName( name ))
        Behaviors.same

      case DeleteWarehouse(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! WarehouseActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case LoadWarehouses(replyTo) =>
        replyTo ! Warehouses(store.load)
        Behaviors.same

      case GetRandomWarehouses(count,replyTo) =>
        val warehouses = WarehouseGenerator.random(count)
        //val store1 = store.++(warehouses)
        replyTo ! Warehouses(warehouses)
        Behaviors.same

      case CreateRandomWarehouses(count,delay,replyTo) =>
        val warehouses = WarehouseGenerator.random(count)
        val store1 = store.++(warehouses,delay)
        replyTo ! Warehouses(warehouses)
        registry(store1.getOrElse(store))

      case ClearWarehouses(replyTo) =>
        val size = store.size
        val store1 = store.clear
        replyTo ! ClearActionPerformed(s"cleared",size)
        registry(store1.getOrElse(store))
    }
  }
}
