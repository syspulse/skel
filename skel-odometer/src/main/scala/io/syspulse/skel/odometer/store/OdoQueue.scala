package io.syspulse.skel.odometer.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import scala.collection.immutable.Queue

import io.syspulse.skel.Command

import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.odometer.server.{OdoUpdateReq}

class OdoQueue {  
  val log = Logger(s"${this}")
      
  @volatile
  var queue = Queue[OdoUpdateReq]()
  
  def push(req:OdoUpdateReq) = {
    queue = queue.prepended(req)    
  }

  def take(limit:Int = 0):Seq[OdoUpdateReq] = {
    limit match {
      case 0 => 
        val rr = queue.toIndexedSeq
        queue = Queue()
        rr
      case n =>
        val rr = queue.take(n)
        queue = queue.drop(n)
        rr
    }        
        
  }
}
