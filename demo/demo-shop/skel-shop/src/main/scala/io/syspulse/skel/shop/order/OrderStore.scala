package io.syspulse.skel.shop.order

import scala.util.{Try,Success}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait OrderStore extends Store[Order] {
  
  def +(order:Order):Try[OrderStore]
  def -(order:Order):Try[OrderStore]
  def -(id:UUID):Try[OrderStore]
  def get(id:UUID):Option[Order]
  def getByItemId(iid:UUID):Seq[Order]
  def getAll:Seq[Order]
  def size:Long

  def load:Seq[Order]
  def clear:Try[OrderStore]

  def ++(orders:Seq[Order],delay:Long = 0L):Try[OrderStore] = {
    orders.foreach { 
      i => this.+(i)
      if(delay>0L) Thread.sleep(delay)
    }
    Success(this)
  }
}

