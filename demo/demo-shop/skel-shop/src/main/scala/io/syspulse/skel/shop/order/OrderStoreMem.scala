package io.syspulse.skel.shop.order

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class OrderStoreMem extends OrderStore {
  val log = Logger(s"${this}")
  
  var orders: Set[Order] = Set()

  def getAll:Seq[Order] = orders.toSeq
  def size:Long = orders.size

  def +(order:Order):Try[OrderStore] = { orders = orders + order; Success(this)}
  def -(id:UUID):Try[OrderStore] = { 
    orders.find(_.id == id) match {
      case Some(order) => { orders = orders - order; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(order:Order):Try[OrderStore] = { 
    val sz = orders.size
    orders = orders - order;
    if(sz == orders.size) Failure(new Exception(s"not found: ${order}")) else Success(this)
  }

  def get(id:UUID):Option[Order] = orders.find(_.id == id)

  def getByItemId(iid:UUID):Seq[Order] = orders.filter(_.item == iid).toSeq

  def load:Seq[Order] = {
    orders = OrderLoader.fromResource().toSet
    getAll
  }

  def clear:Try[OrderStore] = {
    orders = Set()
    Success(this)
  }
}
