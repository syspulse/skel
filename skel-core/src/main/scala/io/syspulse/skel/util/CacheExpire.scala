package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.util.Util

abstract class CacheExpire[K,V](expiry:Long) extends CacheThread { 
  val log = Logger[this.type]
  val empty = Map[K,Cachable[V]]()
  var cache = empty
  
  def key(v:V):K
  
  def get(k:K):Option[V] = {
    cache
      .get(k)
      .filter(_.ts + expiry > System.currentTimeMillis())
      .map(_.v)
  }

  def getOrElse(k:K,v0:V):Option[V] = {
    cache
      .get(k)
      .filter(_.ts + expiry > System.currentTimeMillis()) match {
        case Some(cv) => Some(cv.v)
        case None => Some(v0)
      }
  }

  def upsert(k:K,v0:V,fun:((V) => V)):Option[V] = {
    cache
      .get(k)
      .filter(_.ts + expiry > System.currentTimeMillis())
      .map(cv => {
        val v1 = fun(cv.v)
        cache = cache + (k -> Cachable(v1,System.currentTimeMillis()))      
        Some(v1)
      })
      .getOrElse {
        val v1 = fun(v0)
        cache = cache + (k -> Cachable(v1,System.currentTimeMillis()))      
        Some(v1)
      }
  }
  
  def refresh(v:V,fresh:Int = 0):Option[V] = {
    cache.get(key(v)) match {
      case Some(cv) if(cv.checks < fresh ) => 
        cache = cache + (key(v) -> cv.copy(checks = cv.checks + 1, ts = System.currentTimeMillis()))
        Some(cv.v)
      case None =>
        Some(put(v))
      case _ =>
        None
    }
  }

  def find(v:V):Option[V] = {
    cache
      .get(key(v))
      .filter(_.ts + expiry > System.currentTimeMillis())
      .map(_.v)
  }

  // ATTENTION: Only for testing, fix for fast performance
  
  def put(v:V):V = {
    val c = new Cachable(v,System.currentTimeMillis())
    cache = cache + (key(v) -> c)  
    v
  }

  def put(k:K,v:V):V = {
    val c = new Cachable(v,System.currentTimeMillis())
    cache = cache + (k -> c)  
    v
  }

  def size = cache.size

  def cleanFreq:Long = this.expiry

  def clean() = {
    val now = System.currentTimeMillis()
    val n0 = size
    cache = cache.filter(c => (now - c._2.ts) < expiry)    
    val n1 = size
    //log.trace(s"cache clean: ${n0} -> ${n1}")
    n1
  }

  def sweep() = {
    cache = empty
    0
  }
}

object CacheExpire {
  def apply[K,V](expiry:Long):CacheExpire[K,V] = {
    new CacheExpire[K,V](expiry) {
      override def key(v:V):K = throw new Exception("key not implemented")
    }
  }
}
