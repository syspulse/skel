package io.syspulse.skel.shop.item

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import java.time._

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName
import java.time.ZonedDateTime


final case class Items(items: immutable.Seq[Item])

final case class ItemCreate(name:String, count:Double=1.0, price:Double=0.0)

object ItemRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetItems(replyTo: ActorRef[Items]) extends Command
  final case class GetItem(id:UUID,replyTo: ActorRef[GetItemResponse]) extends Command
  final case class GetItemByName(name:String,replyTo: ActorRef[GetItemResponse]) extends Command
  
  final case class CreateItem(itemCreate: ItemCreate, replyTo: ActorRef[ItemActionPerformed]) extends Command
  final case class DeleteItem(id: UUID, replyTo: ActorRef[ItemActionPerformed]) extends Command

  final case class GetItemResponse(item: Option[Item])
  final case class ItemActionPerformed(description: String,id:Option[UUID])
  final case class ClearActionPerformed(description: String,size:Long)

  final case class LoadItems(replyTo: ActorRef[Items]) extends Command
  final case class ClearItems(replyTo: ActorRef[ClearActionPerformed]) extends Command

  final case class GetRandomItems(count: Long, replyTo: ActorRef[Items]) extends Command
  final case class CreateRandomItems(count: Long, delay:Long, replyTo: ActorRef[Items]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: ItemStore = null

  def apply(store: ItemStore = new ItemStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("item-count") { store.size }

  private def registry(store: ItemStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetItems(replyTo) =>
        replyTo ! Items(store.getAll)
        Behaviors.same
      case CreateItem(itemCreate, replyTo) =>
        val id = UUID.randomUUID()
        val ts = ZonedDateTime.now()
        val item = Item(id,ts, itemCreate.name, itemCreate.count, itemCreate.price)
        val store1 = store.+(item)
        replyTo ! ItemActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
        case GetItem(id, replyTo) =>
        replyTo ! GetItemResponse(store.get(id))
        Behaviors.same

      case GetItemByName(name, replyTo) =>
        replyTo ! GetItemResponse(store.getByName( name ))
        Behaviors.same

      case DeleteItem(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! ItemActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case LoadItems(replyTo) =>
        replyTo ! Items(store.load)
        Behaviors.same

      case GetRandomItems(count,replyTo) =>
        val items = ItemGenerator.random(count)
        //val store1 = store.++(items)
        replyTo ! Items(items)
        Behaviors.same

      case CreateRandomItems(count,delay,replyTo) =>
        val items = ItemGenerator.random(count)
        val store1 = store.++(items,delay)
        replyTo ! Items(items)
        registry(store1.getOrElse(store))

      case ClearItems(replyTo) =>
        val size = store.size
        val store1 = store.clear
        replyTo ! ClearActionPerformed(s"cleared",size)
        registry(store1.getOrElse(store))
    }
  }
}
