package io.syspulse.skel.syslog.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.fields.TextField
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.SearchResponse

import io.syspulse.skel.syslog._
import io.syspulse.skel.syslog.Syslog.ID

import io.syspulse.skel.uri.ElasticURI

class SyslogStoreElastic(elasticUri:String) extends SyslogStore {  
  private val log = Logger(s"${this}")

  val uri = ElasticURI(elasticUri)

  implicit object SyslogHitReader extends HitReader[Syslog] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Syslog] = {
      val source = hit.sourceAsMap
      Success(
        Syslog(
          msg = source("msg").asInstanceOf[String],
          severity = Some(source("severity").asInstanceOf[Int]), 
          scope = Some(source("scope").asInstanceOf[String]),
          ts = source("ts").asInstanceOf[Long], 
        )
      )
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(uri.uri)))

  import ElasticDsl._  
  def all:Seq[Syslog] = {    
    val r = client.execute {
      ElasticDsl
      .search(uri.index)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Syslog].toList
  }

  // slow and memory hungry !
  def size:Long = {
    val r = client.execute {
      ElasticDsl.count(Indexes(uri.index))
    }.await
    r.result.count
  }

  def +(syslog:Syslog):Try[SyslogStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${syslog}"))
  }

  def del(id:ID):Try[SyslogStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def ?(id:ID):Try[Syslog] = {
    search(id.toString).take(1).headOption match {
      case Some(y) => Success(y)
      case None => Failure(new Exception(s"not found: ${id}"))
    }
  }

  def ??(txt:String):List[Syslog] = {
    search(txt)
  }

  def scan(txt:String):List[Syslog] = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["area", "msg"]
      }
    }
    """)        
    }.await

    log.info(s"r=${r}")
    r.result.to[Syslog].toList
  }

  def search(txt:String):List[Syslog] = {   
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .search(uri.index)
        .query(txt)
    }.await

    log.info(s"r=${r}")
    r.result.to[Syslog].toList
  }

  def grep(txt:String):List[Syslog] = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .query {
          ElasticDsl.wildcardQuery("msg",txt)
        }
    }.await

    log.info(s"r=${r}")
    r.result.to[Syslog].toList
  }

  def typing(txt:String):List[Syslog] = {  
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "msg._3gram" ] }}
    """)        
    }.await
    
    log.info(s"r=${r}")
    r.result.to[Syslog].toList
  }
}
