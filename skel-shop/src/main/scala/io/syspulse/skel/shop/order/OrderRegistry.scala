package io.syspulse.skel.shop.order

import scala.util.{Try,Success}
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import java.time._

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName
import java.time.ZonedDateTime
import scala.annotation.tailrec


final case class Orders(orders: immutable.Seq[Order])

final case class OrderCreate(items:Seq[UUID], orderStatus:String)

object OrderRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetOrders(replyTo: ActorRef[Orders]) extends Command
  final case class GetOrder(id:UUID,replyTo: ActorRef[GetOrderResponse]) extends Command
  final case class GetOrderByItemId(iid:UUID,replyTo: ActorRef[Orders]) extends Command
  
  final case class CreateOrder(orderCreate: OrderCreate, replyTo: ActorRef[OrderActionPerformed]) extends Command
  final case class DeleteOrder(id: UUID, replyTo: ActorRef[OrderActionPerformed]) extends Command

  final case class GetOrderResponse(order: Option[Order])
  final case class OrderActionPerformed(description: String,id:Option[UUID])
  final case class ClearActionPerformed(description: String,size:Long)

  final case class LoadOrders(replyTo: ActorRef[Orders]) extends Command
  final case class ClearOrders(replyTo: ActorRef[ClearActionPerformed]) extends Command

  final case class GetRandomOrders(count: Long, replyTo: ActorRef[Orders]) extends Command
  final case class CreateRandomOrders(count: Long, delay:Long, replyTo: ActorRef[Orders]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: OrderStore = null

  def apply(store: OrderStore = new OrderStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("order-count") { store.size }

  private def registry(store: OrderStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetOrders(replyTo) =>
        replyTo ! Orders(store.getAll)
        Behaviors.same
      case CreateOrder(orderCreate, replyTo) =>
        val id = UUID.randomUUID()
        val ts = ZonedDateTime.now()
        
        @tailrec
        def saving(store:Try[OrderStore], orders:Seq[Order]): Try[OrderStore] = {
          orders match {
            case Nil => store
            case o :: ordersLeft => {
              if(store.isFailure)
                return store
              else {
                val r:Try[OrderStore] = store.get.+(o)
                saving(r,ordersLeft)
              }
            }
          }
        }

        val orders = orderCreate.items.map( iid => Order(id, ts, iid, orderCreate.orderStatus ))
        val store1 = saving(Success(store),orders)
        
        replyTo ! OrderActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
      case GetOrder(id, replyTo) =>
        replyTo ! GetOrderResponse(store.get(id))
        Behaviors.same

      case GetOrderByItemId(iid, replyTo) =>
        replyTo ! Orders(store.getByItemId(iid))
        Behaviors.same

      case DeleteOrder(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! OrderActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case LoadOrders(replyTo) =>
        replyTo ! Orders(store.load)
        Behaviors.same

      case GetRandomOrders(count,replyTo) =>
        val orders = OrderGenerator.random(count)
        //val store1 = store.++(orders)
        replyTo ! Orders(orders)
        Behaviors.same

      case CreateRandomOrders(count,delay,replyTo) =>
        val orders = OrderGenerator.random(count)
        val store1 = store.++(orders,delay)
        replyTo ! Orders(orders)
        registry(store1.getOrElse(store))

      case ClearOrders(replyTo) =>
        val size = store.size
        val store1 = store.clear
        replyTo ! ClearActionPerformed(s"cleared",size)
        registry(store1.getOrElse(store))
    }
  }
}
