package io.syspulse.skel.yell.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.yell._
import io.syspulse.skel.store.Store
import io.syspulse.skel.yell.Yell.ID

trait YellStore extends Store[Yell,ID] {
  
  def +(yell:Yell):Try[YellStore]
  def -(yell:Yell):Try[YellStore]
  def del(id:ID):Try[YellStore]
  def ?(id:ID):Try[Yell]
  def all:Seq[Yell]
  def size:Long

  def ??(txt:String):List[Yell]

  def connect(config:Config):YellStore = this

  def scan(txt:String):List[Yell]
  def search(txt:String):List[Yell]
  def grep(txt:String):List[Yell]
}
