package io.syspulse.skel.ingest.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.ingest._

class IngestStoreMem[I] extends IngestStore[I] {
  val log = Logger(s"${this}")
  
  var ingests: Map[I,Ing[I]] = Map()

  def all:Seq[Ing[I]] = ingests.values.toSeq

  def size:Long = ingests.size

  def +(ingest:Ing[I]):Try[Ing[I]] = { 
    ingests = ingests + (ingest.getId -> ingest)
    log.info(s"${ingest}")
    Success(ingest)
  }

  def del(id:I):Try[I] = {
    val sz = ingests.size
    ingests = ingests - id;
    log.info(s"${id}")
    if(sz == ingests.size) Failure(new Exception(s"not found: ${id}")) else Success(id)
  }

  def ?(id:I):Try[Ing[I]] = ingests.get(id) match {
    case Some(i) => Success(i)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def ??(txt:String):List[Ing[I]] = {
    ingests.values.filter(v => 
      // v.desc.matches(txt) || 
      v.searchables().matches(txt)
    ).toList
  }

  def scan(txt:String):List[Ing[I]] = ??(txt)
  def search(txt:String):List[Ing[I]] = ??(txt)
  def grep(txt:String):List[Ing[I]] = ??(txt)
  def typing(txt:String):List[Ing[I]] = ??(txt)
}
