package io.syspulse.skel.service

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ServiceStore extends Store[Service,UUID] {
  
  def +(service:Service):Try[ServiceStore]
  def -(service:Service):Try[ServiceStore]
  def del(id:UUID):Try[ServiceStore]
  def ?(id:UUID):Option[Service]
  def all:Seq[Service]
  def size:Long
}

