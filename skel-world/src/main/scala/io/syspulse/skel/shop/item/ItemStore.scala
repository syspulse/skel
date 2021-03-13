package io.syspulse.skel.shop.item

import scala.util.{Try,Success}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ItemStore extends Store[Item] {
  
  def +(item:Item):Try[ItemStore]
  def -(item:Item):Try[ItemStore]
  def -(id:UUID):Try[ItemStore]
  def get(id:UUID):Option[Item]
  def getByName(name:String):Option[Item]
  def getAll:Seq[Item]
  def size:Long

  def load:Seq[Item]
  def clear:Try[ItemStore]

  def ++(items:Seq[Item],delay:Long = 0L):Try[ItemStore] = {
    items.foreach { 
      i => this.+(i)
      if(delay>0L) Thread.sleep(delay)
    }
    Success(this)
  }
}

