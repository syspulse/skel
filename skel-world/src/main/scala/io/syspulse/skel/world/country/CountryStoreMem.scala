package io.syspulse.skel.world.country

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class CountryStoreMem extends CountryStore {
  val log = Logger(s"${this}")
  
  var countrys: Set[Country] = Set()

  def getAll:Seq[Country] = countrys.toSeq
  def size:Long = countrys.size

  def +(country:Country):Try[CountryStore] = { countrys = countrys + country; Success(this)}
  def -(id:UUID):Try[CountryStore] = { 
    countrys.find(_.id == id) match {
      case Some(country) => { countrys = countrys - country; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(country:Country):Try[CountryStore] = { 
    val sz = countrys.size
    countrys = countrys - country;
    if(sz == countrys.size) Failure(new Exception(s"not found: ${country}")) else Success(this)
  }

  def get(id:UUID):Option[Country] = countrys.find(_.id == id)

  def getByName(name:String):Option[Country] = countrys.find( c => 
    (if(name.size==2) c.iso.compareToIgnoreCase(name)
    else c.name.compareToIgnoreCase(name)) == 0
  )

  def load:Seq[Country] = {
    countrys = CountryLoader.fromResource().toSet
    getAll
  }

  def clear:Try[CountryStore] = {
    countrys = Set()
    Success(this)
  }
}
