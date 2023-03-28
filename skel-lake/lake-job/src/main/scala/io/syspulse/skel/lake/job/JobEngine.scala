package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

trait JobEngine {  
  def all():Try[Seq[Job]]
  def ask(xid:String):Try[Job]
  def get(xid:String):Try[Job]
  def create(name:String,conf:Map[String,String]=Map()):Try[Job]
  def del(xid:String):Try[String]
  def run(job:Job,script:String):Try[Job]
}

class JobStdout extends JobEngine {
  def all():Try[Seq[Job]] = Failure(new Exception(s"not supported"))
  
  def ask(xid:String):Try[Job] = Failure(new Exception(s"not supported: ${xid}"))
  def get(xid:String):Try[Job] = ask(xid)

  def create(name:String,conf:Map[String,String]=Map()):Try[Job] = Failure(new Exception(s"not supported: ${name}"))
  
  def del(xid:String):Try[String] = Failure(new Exception(s"not supported: ${xid}"))

  def run(job:Job,script:String):Try[Job] = Failure(new Exception(s"not supported: ${job}"))
}