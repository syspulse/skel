package io.syspulse.skel.telemetry.store

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

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.Telemetry.ID

import io.syspulse.skel.telemetry._
import io.syspulse.skel.uri.ElasticURI

class TelemetryStoreElastic(elasticUri:String) extends TelemetryStore {
  private val log = Logger(s"${this}")

  val uri = ElasticURI(elasticUri)

  implicit object TelemetryHitReader extends HitReader[Telemetry] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Telemetry] = {
      val source = hit.sourceAsMap
      Success(Telemetry(source("id").asInstanceOf[String], source("ts").asInstanceOf[Long], source("data").asInstanceOf[List[AnyRef]]))
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(elasticUri)))

  import ElasticDsl._  

  def clean():Try[TelemetryStore] = { Failure(new UnsupportedOperationException) }
  
  def all:Seq[Telemetry] = {    
    val r = client.execute {
      ElasticDsl
      .search(uri.index)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toList
  }

  // slow and memory hungry !
  def size:Long = {
    val r = client.execute {
      ElasticDsl.count(Indexes(uri.index))
    }.await
    r.result.count
  }

  def +(telemetry:Telemetry):Try[TelemetryStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${telemetry}"))
  }

  def del(id:ID):Try[TelemetryStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def -(telemetry:Telemetry):Try[TelemetryStore] = {     
    Failure(new UnsupportedOperationException(s"not implemented: ${telemetry}"))
  }

  def ?(id:ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry] = {
    search(id.toString,ts0,ts1)
  }

  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = {
    search(txt,ts0,ts1)
  }

  def scan(txt:String):List[Telemetry] = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["id", "ts0", "ts1"]
      }
    }
    """)
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toList
  }

  def search(txt:String,ts0:Long,ts1:Long):List[Telemetry] = {   
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(
        
        //.search(uri.index)
        //.query(txt)
        s"""
        "query": {
          "bool" : {             
            "must" : {
              "range": {
                "ts": {
                  "gte": ${ts0},
                  "lte": ${ts1}
                }
              }
            },
            "must" : {
              "query_string": {
                "query": "${txt}",
                "default_operator": "AND"
              }
            }
          }
        }
        """)
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toList
    
    // r match {
    //   case failure: RequestFailure => List.empty
    //   case results: RequestSuccess[SearchResponse] => r.as[Telemetry] //results.result.hits.hits.toList
    //   case results: RequestSuccess[_] => results.result
    // }
  }

  def grep(txt:String):List[Telemetry] = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .query {
          ElasticDsl.wildcardQuery("title",txt)
        }
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toList
  }

  def typing(txt:String):List[Telemetry] = {  
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "id", "id._3gram" ] }}
    """)        
    }.await
    
    log.info(s"r=${r}")
    r.result.to[Telemetry].toList
  }
}
