package io.syspulse.skel.service

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ServiceStore extends Store[Service,UUID] {
  def getKey(s: Service): UUID = s.id
  def +(service:Service):Try[ServiceStore]
  def del(id:UUID):Try[ServiceStore]
  def ?(id:UUID):Try[Service]
  def all:Seq[Service]
  def size:Long
}

