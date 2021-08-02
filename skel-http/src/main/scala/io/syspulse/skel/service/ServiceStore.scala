package io.syspulse.skel.service

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ServiceStore extends Store[Service] {
  
  def +(service:Service):Try[ServiceStore]
  def -(service:Service):Try[ServiceStore]
  def -(id:UUID):Try[ServiceStore]
  def get(id:UUID):Option[Service]
  def getAll:Seq[Service]
  def size:Long
}

