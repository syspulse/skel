package io.syspulse.skel.service

import scala.util.Try
import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ServiceStore extends Store[Service,UUID] {
  def getKey(s: Service): UUID = s.id
  def +(service:Service):Future[Service]
  def del(id:UUID):Future[UUID]
  def ?(id:UUID):Future[Service]
  def all:Future[Seq[Service]]
  def size:Future[Long]
}
