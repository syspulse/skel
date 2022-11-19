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

class IngestStoreMem[ID] extends IngestStore[IN[ID],ID] {
  val log = Logger(s"${this}")
  
  var ingests: Map[ID,IN[ID]] = Map()

  def all:Seq[IN[ID]] = ingests.values.toSeq

  def size:Long = ingests.size

  def +(ingest:IN[ID]):Try[IngestStore[IN[ID],ID]] = { 
    ingests = ingests + (ingest.id -> ingest)
    log.info(s"${ingest}")
    Success(this)
  }

  def del(id:ID):Try[IngestStore[IN[ID],ID]] = {
    val sz = ingests.size
    ingests = ingests - id;
    log.info(s"${id}")
    if(sz == ingests.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def -(ingest:IN[ID]):Try[IngestStore[IN[ID],ID]] = {     
    del(ingest.id)
  }

  def ?(id:ID):Option[IN[ID]] = ingests.get(id)

  def ??(txt:String):List[IN[ID]] = {
    ingests.values.filter(v => 
      // v.desc.matches(txt) || 
      v.searchables().matches(txt)
    ).toList
  }

  def scan(txt:String):List[IN[ID]] = ??(txt)
  def search(txt:String):List[IN[ID]] = ??(txt)
  def grep(txt:String):List[IN[ID]] = ??(txt)
  def typing(txt:String):List[IN[ID]] = ??(txt)
}
