package io.syspulse.skel.world.currency

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CurrencyStore extends Store[Currency] {
  
  def +(currency:Currency):Try[CurrencyStore]
  def -(currency:Currency):Try[CurrencyStore]
  def -(id:UUID):Try[CurrencyStore]
  def get(id:UUID):Option[Currency]
  def getByName(name:String):Option[Currency]
  def getAll:Seq[Currency]
  def size:Long

  def load:Seq[Currency]
  def clear:Try[CurrencyStore]
}

