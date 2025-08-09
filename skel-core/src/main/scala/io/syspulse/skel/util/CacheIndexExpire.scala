package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.util.Util

abstract class CacheIndexExpire[K,V,I](expiry:Long) extends CacheExpire[K,V](expiry) {   
  
  def index(v:V):I
  
  // ATTENTION: Only for testing, fix for fast performance
  def findByIndex(i:I):Option[V] = {
    cache
      .values
      .filter(_.ts + expiry > System.currentTimeMillis())
      .find(v => index(v.v) == i)
      .map(_.v)
  }    
}
