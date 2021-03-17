package io.syspulse.skel.world.country

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CountryStore extends Store[Country] {
  
  def +(country:Country):Try[CountryStore]
  def -(country:Country):Try[CountryStore]
  def -(id:UUID):Try[CountryStore]
  def get(id:UUID):Option[Country]
  def getByName(name:String):Option[Country]
  def getAll:Seq[Country]
  def size:Long

  def load:Seq[Country]
  def clear:Try[CountryStore]
}

object CountryStore {
  val default = CountryLoader.fromResource()
  def getDefault:Seq[Country] = default
}

