package io.syspulse.skel.world.country

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
//import java.util.UUID

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class Countrys(countrys: immutable.Seq[Country])

final case class CountryCreate(name:String, short:String)

object CountryRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetCountrys(replyTo: ActorRef[Countrys]) extends Command
  final case class GetCountry(id:UUID,replyTo: ActorRef[GetCountryResponse]) extends Command
  final case class GetCountryByName(name:String,replyTo: ActorRef[GetCountryResponse]) extends Command
  
  final case class CreateCountry(countryCreate: CountryCreate, replyTo: ActorRef[CountryActionPerformed]) extends Command
  final case class DeleteCountry(id: UUID, replyTo: ActorRef[CountryActionPerformed]) extends Command

  final case class GetCountryResponse(country: Option[Country])
  final case class CountryActionPerformed(description: String,id:Option[UUID])
  final case class DeleteActionPerformed(description: String,size:Long)

  final case class ReloadCountrys(replyTo: ActorRef[Countrys]) extends Command
  final case class DeleteCountrys(replyTo: ActorRef[DeleteActionPerformed]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: CountryStore = null

  def apply(store: CountryStore = new CountryStoreCache): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("country-count") { store.size }

  private def registry(store: CountryStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetCountrys(replyTo) =>
        replyTo ! Countrys(store.getAll)
        Behaviors.same
      case CreateCountry(countryCreate, replyTo) =>
        val id = UUID.randomUUID()
        val country = Country(id,countryCreate.name,countryCreate.short)
        val store1 = store.+(country)
        replyTo ! CountryActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      
        case GetCountry(id, replyTo) =>
        replyTo ! GetCountryResponse(store.get(id))
        Behaviors.same

      case GetCountryByName(name, replyTo) =>
        replyTo ! GetCountryResponse(store.getByName( name ))
        Behaviors.same

      case DeleteCountry(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! CountryActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))

      case ReloadCountrys(replyTo) =>
        replyTo ! Countrys(store.reloadAll)
        Behaviors.same

      case DeleteCountrys(replyTo) =>
        val size = store.size
        val store1 = store.deleteAll
        replyTo ! DeleteActionPerformed(s"ALL DELETED",size)
        registry(store1.getOrElse(store))
    }
  }
}
