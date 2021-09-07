package io.syspulse.skel.world.currency

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
//import java.util.UUID

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class Currencys(currencys: immutable.Seq[Currency])

final case class CurrencyCreate(name:String, code:String, country:String="")

object CurrencyRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetCurrencys(replyTo: ActorRef[Currencys]) extends Command
  final case class GetCurrency(id:UUID,replyTo: ActorRef[GetCurrencyResponse]) extends Command
  final case class GetCurrencyByName(name:String,replyTo: ActorRef[GetCurrencyResponse]) extends Command
  
  final case class CreateCurrency(currencyCreate: CurrencyCreate, replyTo: ActorRef[CurrencyActionPerformed]) extends Command
  final case class DeleteCurrency(id: UUID, replyTo: ActorRef[CurrencyActionPerformed]) extends Command

  final case class GetCurrencyResponse(currency: Option[Currency])
  final case class CurrencyActionPerformed(description: String,id:Option[UUID])
  final case class ClearActionPerformed(description: String,size:Long)

  final case class LoadCurrencys(replyTo: ActorRef[Currencys]) extends Command
  final case class ClearCurrencys(replyTo: ActorRef[ClearActionPerformed]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: CurrencyStore = null

  def apply(store: CurrencyStore = new CurrencyStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("currency-count") { store.size }

  private def registry(store: CurrencyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetCurrencys(replyTo) =>
        replyTo ! Currencys(store.getAll)
        Behaviors.same
      case CreateCurrency(currencyCreate, replyTo) =>
        val id = UUID.randomUUID()
        val currency = Currency(id,currencyCreate.name,currencyCreate.code,0,currencyCreate.country)
        val store1 = store.+(currency)
        replyTo ! CurrencyActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
        case GetCurrency(id, replyTo) =>
        replyTo ! GetCurrencyResponse(store.get(id))
        Behaviors.same

      case GetCurrencyByName(name, replyTo) =>
        replyTo ! GetCurrencyResponse(store.getByName( name ))
        Behaviors.same

      case DeleteCurrency(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! CurrencyActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case LoadCurrencys(replyTo) =>
        replyTo ! Currencys(store.load)
        Behaviors.same

      case ClearCurrencys(replyTo) =>
        val size = store.size
        val store1 = store.clear
        replyTo ! ClearActionPerformed(s"cleared",size)
        registry(store1.getOrElse(store))
    }
  }
}
