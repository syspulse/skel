package io.syspulse.skel.service

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._

import io.prometheus.client.Gauge

final case class ServiceCode(id:UUID,code: String)
final case class Services(services: immutable.Seq[Service])

// create Service Parameters
final case class ServiceCreate(secret: String,name:String, uri:String, period:Option[Int])

object ServiceRegistry {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetServices(replyTo: ActorRef[Services]) extends Command
  final case class GetService(id:UUID,replyTo: ActorRef[GetServiceResponse]) extends Command
  final case class GetServiceCode(id:UUID,replyTo: ActorRef[GetServiceCodeResponse]) extends Command
  final case class CreateService(serviceCreate: ServiceCreate, replyTo: ActorRef[ServiceActionPerformed]) extends Command
  final case class DeleteService(id: UUID, replyTo: ActorRef[ServiceActionPerformed]) extends Command

  final case class GetServiceResponse(service: Option[Service])
  final case class GetServiceCodeResponse(serviceCode: Option[ServiceCode])
  final case class ServiceActionPerformed(description: String,id:Option[UUID])

  // this var reference is unfortunately needed for Metrics access
  var store: ServiceStore = null //new ServiceStoreDB //new ServiceStoreMem

  def apply(store: ServiceStore = new ServiceStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  val metricStoreSize = Gauge.build().name("skel_http_store_size").help("Store Size").register()
  
  private def registry(store: ServiceStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetServices(replyTo) =>
        replyTo ! Services(store.getAll)
        Behaviors.same
      case CreateService(serviceCreate, replyTo) =>
        val id = UUID.randomUUID()
        val service = Service(id,serviceCreate.secret,serviceCreate.name,serviceCreate.uri,serviceCreate.period.getOrElse(30))
        val store1 = store.+(service)
        metricStoreSize.set(store1.map(s => s.size.toDouble).getOrElse(store.size.toDouble))
        replyTo ! ServiceActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      case GetService(id, replyTo) =>
        replyTo ! GetServiceResponse(store.get(id))
        Behaviors.same
      case DeleteService(id, replyTo) =>
        val store1 = store.-(id)
        metricStoreSize.set(store1.map(s => s.size.toDouble).getOrElse(store.size.toDouble))
        replyTo ! ServiceActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
