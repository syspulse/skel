package io.syspulse.skel.ai

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.Ingestable

case class Ai(
  question:String,  
  answer:Option[String] = None,

  oid:Option[String] = None,
  model:Option[String] = None,

  ts:Long = System.currentTimeMillis(),
  
  tags:Seq[String] = Seq(),  
  meta: Map[String,Map[String,Any]] = Map(),

  xid:Option[String] = None, // conversation id 
) extends Ingestable
