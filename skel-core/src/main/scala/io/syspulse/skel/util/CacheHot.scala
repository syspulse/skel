package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import io.syspulse.skel.util.Util

import scala.collection.mutable

abstract class CacheHot[K,V,I](expiry:Long = 1000L * 60L * 10L) extends CacheThread { 
  val log = Logger[this.type]
  var cache = Map[K,Cachable[V]]()
  var cacheIndex = Map[I,Cachable[V]]()

  def key(v:V):K
  def index(v:V):I
  
  def get(k:K):Option[V] = {
    cache
      .get(k)
      .map(v => {
        v.ts = System.currentTimeMillis()
        v.v
      })
  }

  def getOrElse(k:K,v0:V):Option[V] = {
    cache
      .get(k)
      .map(v => {
        v.ts = System.currentTimeMillis()
        v
      }) match {
        case Some(cv) => Some(cv.v)
        case None => Some(v0)
      }
  }

  def upsert(k:K,v0:V):Option[V] = {
    cache = cache + (k -> Cachable(v0,System.currentTimeMillis()))      
    Some(v0)
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
      .map(_.copy(ts = System.currentTimeMillis()))
      .map(_.v)
  }

  def findByIndex(i:I):Option[V] = {
    cacheIndex
      .get(i)
      .map(_.copy(ts = System.currentTimeMillis()))
      .map(_.v)
  }
  
  def put(v:V):V = {
    val c = new Cachable(v,System.currentTimeMillis())
    cache = cache + (key(v) -> c)
    cacheIndex = cacheIndex + (index(v) -> c)
    v
  }

  def size = cache.size

  def cleanFreq:Long = this.expiry
  
  def clean():Long = {
    val now = System.currentTimeMillis()
    val n0 = size
    cache = cache.filter(c => (now - c._2.ts) < expiry)
    cacheIndex = cacheIndex.filter(c => (now - c._2.ts) < expiry)
    val n1 = size
    log.info(s"cache clean: ${n0} -> ${n1}")
    n1
  }


}
