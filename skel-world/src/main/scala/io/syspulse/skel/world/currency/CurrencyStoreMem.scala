package io.syspulse.skel.world.currency

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class CurrencyStoreMem extends CurrencyStore {
  val log = Logger(s"${this}")
  
  var currencys: Set[Currency] = Set()

  def getAll:Seq[Currency] = currencys.toSeq
  def size:Long = currencys.size

  def +(currency:Currency):Try[CurrencyStore] = { currencys = currencys + currency; Success(this)}
  def -(id:UUID):Try[CurrencyStore] = { 
    currencys.find(_.id == id) match {
      case Some(currency) => { currencys = currencys - currency; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(currency:Currency):Try[CurrencyStore] = { 
    val sz = currencys.size
    currencys = currencys - currency;
    if(sz == currencys.size) Failure(new Exception(s"not found: ${currency}")) else Success(this)
  }

  def get(id:UUID):Option[Currency] = currencys.find(_.id == id)

  def getByName(name:String):Option[Currency] = currencys.find( c => 
    (if(name.size==3) c.code.compareToIgnoreCase(name)
    else c.name.compareToIgnoreCase(name)) == 0
  )

  def load:Seq[Currency] = {
    currencys = CurrencyLoader.fromResource().toSet
    getAll
  }

  def clear:Try[CurrencyStore] = {
    currencys = Set()
    Success(this)
  }
}
