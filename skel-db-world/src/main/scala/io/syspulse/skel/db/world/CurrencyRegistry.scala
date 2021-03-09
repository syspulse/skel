package io.syspulse.skel.db.world

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
//import java.util.UUID

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class Currencys(otps: immutable.Seq[Currency])

final case class CurrencyCreate(name:String, short:String)

object CurrencyRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetCurrencys(replyTo: ActorRef[Currencys]) extends Command
  final case class GetCurrency(id:UUID,replyTo: ActorRef[GetCurrencyResponse]) extends Command
  final case class GetCurrencyByName(name:String,replyTo: ActorRef[GetCurrencyResponse]) extends Command
  
  final case class CreateCurrency(currencyCreate: CurrencyCreate, replyTo: ActorRef[CurrencyActionPerformed]) extends Command
  final case class DeleteCurrency(id: UUID, replyTo: ActorRef[CurrencyActionPerformed]) extends Command

  final case class GetCurrencyResponse(currency: Option[Currency])
  final case class CurrencyActionPerformed(description: String,id:Option[UUID])

  // this var reference is unfortunately needed for Metrics access
  // var store: CurrencyStore = new CurrencyStoreDB //new OtpStoreCache

  // def apply(store: CurrencyStore = new CurrencyStoreCache): Behavior[io.syspulse.skel.Command] = {
  //   this.store = store
  //   registry(store)
  // }

  // override lazy val metricBaseName = MetricName("")
  // metrics.gauge("currency-count") { store.size }

  // private def registry(store: CurrencyStore): Behavior[io.syspulse.skel.Command] = {
  //   this.store = store

  //   Behaviors.receiveMessage {
  //     case GetCurrencys(replyTo) =>
  //       replyTo ! Currencys(store.getAll)
  //       Behaviors.same
  //     case CreateCurrency(currencyCreate, replyTo) =>
  //       val id = UUID.randomUUID()
  //       val currency = Currency(id,currencyCreate.name,currencyCreate.short)
  //       val store1 = store.+(currency)
  //       replyTo ! CurrencyActionPerformed(s"created",Some(id))
  //       registry(store1.getOrElse(store))
  //     case GetCurrency(id, replyTo) =>
  //       replyTo ! GetCurrencyResponse(store.get(id))
  //       Behaviors.same
  //     case DeleteCurrency(id, replyTo) =>
  //       val store1 = store.-(id)
  //       replyTo ! CurrencyActionPerformed(s"deleted",Some(id))
  //       registry(store1.getOrElse(store))
  //   }
  // }
}
