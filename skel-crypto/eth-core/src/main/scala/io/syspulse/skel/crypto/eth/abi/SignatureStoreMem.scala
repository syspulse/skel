package io.syspulse.skel.crypto.eth.abi

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class SignatureStoreMem[T <: AbiSignature] extends SignatureStore[T] {
  val log = Logger(s"${this}")

  var sigs: Map[String,Vector[T]] = Map()

  private def allSync:Seq[T] = sigs.values.foldLeft(Seq[T]())(_ ++ _)
  private def sizeSync:Long = sigs.values.foldLeft(0)(_ + _.size).toLong

  def all:Future[Seq[T]] = Future.successful(allSync)

  def all(from:Option[Int],size:Option[Int]):(Seq[T],Long) = {
    var n = 0
    val aa =
      sigs.takeWhile{ case(sig,vv) => {
        val b = n < (from.getOrElse(0) + size.getOrElse(10))
        n = n + vv.size
        b
      }}.values.flatten.toSeq

    (aa.drop(from.getOrElse(0)).take(size.getOrElse(10)), sizeSync)
  }

  def size:Future[Long] = Future.successful(sizeSync)

  def +(sig:T):Future[T] = {
    sigs = sigs + { sig.getId().toLowerCase -> { sigs.getOrElse(sig.getId().toLowerCase(),Vector[T]()).appended(sig).sortBy(_.getVer())  }}
    Future.successful(sig)
  }

  def del(id:(String,Int)):Future[(String,Int)] = {
    val v = sigs.get(id._1.toLowerCase())
    if(v.isDefined) {
      val sig = id._1.toLowerCase -> { v.get.filter(_.getVer() != id._2) }
      sigs = sigs + { sig }
      Future.successful(id)
    } else {
      Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def ?(id:(String,Int)):Future[T] = { sigs.get(id._1.toLowerCase()) match {
    case Some(v) => v.find(_.getVer() == id._2)
    case None => None
  }} match {
    case Some(o) => Future.successful(o)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def ??(id:String):Try[Vector[T]] = sigs.get(id.toLowerCase()) match {
    case Some(v) => Success(v)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def first(id:String):Try[T] = sigs.get(id.toLowerCase()) match {
    case Some(v) => Success(v.head)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def findByTex(tex:String):Try[T] = {
    allSync.find(_.getTex().toLowerCase == tex.toLowerCase()) match {
      case Some(u) => Success(u)
      case None => Failure(new Exception(s"not found: ${tex}"))
    }
  }

  def search(txt:String,from:Option[Int],size:Option[Int]):(Seq[T],Long) = {
    if(txt.trim.size < 3)
      return (Seq(),0L)

    val term = txt.toLowerCase + ".*"

    val vv = sigs.values.flatten.filter(v => {
        v.getId().toLowerCase.matches(term) ||
        v.getTex().toLowerCase.matches(term)
    })

    (vv.drop(from.getOrElse(0)).take(size.getOrElse(10)).toList,vv.size)
  }

}
