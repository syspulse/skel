package io.syspulse.skel.crypto.eth.abi

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class SignatureStoreMem[T <: AbiSignature] extends SignatureStore[T] {
  val log = Logger(s"${this}")
  
  var sigs: Map[String,Vector[T]] = Map()

  def all:Seq[T] = sigs.values.foldLeft(Seq[T]())(_ ++ _)

  def all(from:Option[Int],size:Option[Int]):(Seq[T],Long) = {
    val aa = all
    (aa.drop(from.getOrElse(0)).take(size.getOrElse(10)),aa.size)
  }

  def size:Long = sigs.values.foldLeft(0)(_ + _.size)

  def +(sig:T):Try[SignatureStoreMem[T]] = { 
    sigs = sigs + { sig.getId().toLowerCase -> { sigs.getOrElse(sig.getId().toLowerCase(),Vector[T]()).appended(sig).sortBy(_.getVer())  }}
    //sigs = sigs + (sig.getKey() -> sig)    
    Success(this)
  }

  // override def del(sig:T):Try[SignatureStoreMem[T]] = { 
  //   val key = getKey(sig)
  //   val v = sigs.get(key)
  //   if(v.isDefined) {
  //     sigs = sigs + { id -> { v.get.filter(_.getVer() != sig.getVer()) }}
  //     Success(this)
  //   } else {
  //     Failure(new Exception(s"not found: ${id}"))
  //   }
  // }

  def del(id:(String,Int)):Try[SignatureStoreMem[T]] = { 
    val v = sigs.get(id._1.toLowerCase())
    if(v.isDefined) {
      sigs = sigs + { id._1.toLowerCase -> { v.get.filter(_.getVer() != id._2) }}
      Success(this)
    } else {
      Failure(new Exception(s"not found: ${id}"))
    }
  }
  
  // def ???(id:String,max:Int = 10, limit:Int = 10):Try[T] = Range(0,max).foldLeft(Seq[T]())(
  //   (o,i) => 
  //     if(o.size < limit)
  //       o ++ {sigs.get(AbiSignature.getKey(id,Some(i))) match {
  //         case Some(o) => Seq(o)
  //         case None => Seq()
  //       }}
  //     else 
  //       o
  // )
  // .headOption match {
  //   case Some(o) => Success(o)
  //   case None => Failure(new Exception(s"not found: ${id}"))
  // }

  def ?(id:(String,Int)):Try[T] = { sigs.get(id._1.toLowerCase()) match {
    case Some(v) => v.find(_.getVer() == id._2)
    case None => None
  }} match {
    case Some(o) => Success(o)
    case None => Failure(new Exception(s"not found: ${id}"))
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
    all.find(_.getTex().toLowerCase == tex.toLowerCase()) match {
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
