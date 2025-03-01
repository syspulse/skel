package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.util.Util

abstract class CacheExpire[K,V,I](expiry:Long = 1000L * 60L * 10L) extends CacheThread { 
  val log = Logger[this.type]
  var cache = Map[K,Cachable[V]]()
  //var cacheIndex = Map[I,Cachable[V]]()
  
  def key(v:V):K
  def index(v:V):I
  
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
  def findByIndex(i:I):Option[V] = {
    cache
      .values
      .filter(_.ts + expiry > System.currentTimeMillis())
      .find(v => index(v.v) == i)
      .map(_.v)
  }
  
  def put(v:V):V = {
    val c = new Cachable(v,System.currentTimeMillis())
    cache = cache + (key(v) -> c)
    //cacheIndex = cacheIndex + (index(v) -> c)
    v
  }

  def size = cache.size

  def cleanFreq:Long = this.expiry

  def clean() = {
    val now = System.currentTimeMillis()
    val n0 = size
    cache = cache.filter(c => (now - c._2.ts) < expiry)
    //cacheIndex = cacheIndex.filter(c => (now - c._2.ts) < expiry)
    val n1 = size
    log.info(s"cache clean: ${n0} -> ${n1}")
    n1
  }
}
