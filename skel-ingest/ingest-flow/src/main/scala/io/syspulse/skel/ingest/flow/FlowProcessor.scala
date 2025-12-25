package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext}
import io.jvm.uuid._
import scala.util.{Try,Failure,Success}

import spray.json._

import akka.util.ByteString
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.ingest._

object FlowProcessors {
  private val log = Logger(s"${this}")
  
  val processors = Map(
    FlowProcessorNone.name -> FlowProcessorNone,
    FlowProcessorPrint.name -> FlowProcessorPrint,
    FlowProcessorDedup.name -> FlowProcessorDedup,
  )

  def find(name:String):Option[FlowProcessor[String]] = processors.get(name)
  def create(uri:String):Try[FlowProcessorRun[String]] = {
    uri.split("://").toList match {
      case name :: Nil => find(name).map(fp => Try(fp.create(uri))).getOrElse(Failure(new Exception(s"Flow processor not found: '${name}'")))
      case name :: params => find(name).map(fp => Try(fp.create(uri))).getOrElse(Failure(new Exception(s"Flow processor not found: '${name}'")))
      case _ => Failure(new Exception(s"Invalid flow processor URI: '${uri}'"))
    }
  }
  
}

abstract class FlowProcessor[T](val name:String) {
  def create(uri:String):FlowProcessorRun[T]
}

trait FlowProcessorRun[T] {
  def name:String
  def id:String
  def process:Flow[T,Seq[T],_]
}

object FlowProcessorNone extends FlowProcessor[String]("none") {
  def create(uri:String):FlowProcessorRun[String] = new FlowProcessorNoneRun(uri)
}

class FlowProcessorNoneRun(uri:String) extends FlowProcessorRun[String] {
  def name:String = FlowProcessorNone.name
  val id:String = UUID.randomUUID().toString
  def process:Flow[String,Seq[String],_] = Flow[String].map(s => Seq(s))
}

object FlowProcessorPrint extends FlowProcessor[String]("print") {
  def create(uri:String):FlowProcessorRun[String] = new FlowProcessorPrintRun(uri)
}

class FlowProcessorPrintRun(uri:String) extends FlowProcessorRun[String] {
  val prefix = uri.split("://").last
  def name:String = FlowProcessorPrint.name
  val id:String = UUID.randomUUID().toString
  def process:Flow[String,Seq[String],_] = Flow[String].map(s => { println(s"${prefix} ${s}"); Seq(s) })
}

object FlowProcessorDedup extends FlowProcessor[String]("dedup") {
  def create(uri:String):FlowProcessorRun[String] = new FlowProcessorDedupRun(uri)
}

class FlowProcessorDedupRun(uri:String) extends FlowProcessorRun[String] {  
  val (window) = uri.split("://").toList match {
    case _ :: n :: Nil => n.toLong    
    case _ => 5000L
  }

  def name:String = FlowProcessorDedup.name
  val id:String = UUID.randomUUID().toString
  def process:Flow[String,Seq[String],_] = {
    Flow[String]
      .groupedWithin(Int.MaxValue,FiniteDuration(window,TimeUnit.MILLISECONDS))
      .statefulMapConcat { () =>
        var state = Set.empty[String]
        var lastTs = System.currentTimeMillis()
        (mm) => {
          // First deduplicate within the batch, then filter against state
          val batchUniq = mm.distinct
          val uniq = batchUniq.filterNot(state.contains)
          state = state ++ uniq
          val now = System.currentTimeMillis()
          if( (now - lastTs) > window ) {
            // Clean up state periodically to prevent unbounded growth
            // Keep only the most recent unique strings from current batch
            state = uniq.take(2).toSet
            lastTs = now
          }

          Console.err.println(s"uniq: ${uniq} (state=${state})")
          Seq(uniq)
        }
      }
    }
}