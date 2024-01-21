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
import io.syspulse.skel.telemetry.server.Telemetrys

class TelemetryStoreElastic(elasticUri:String) extends TelemetryStore {
  private val log = Logger(s"${this}")

  val uri = ElasticURI(elasticUri)

  implicit object TelemetryHitReader extends HitReader[Telemetry] {
    // special very slow 'intelligent' mapper to parse different default formats
    override def read(hit: Hit): Try[Telemetry] = {
      val source = hit.sourceAsMap
      val data = List(
        try { Some(source("data").asInstanceOf[List[AnyRef]]) } catch {case e:Exception => None},
        try { Some(source("v").asInstanceOf[List[AnyRef]]) } catch { case e:Exception => None},
        try { Some(List(source("v").asInstanceOf[AnyRef])) } catch { case e:Exception => None},
      ).flatten   
      
      data.headOption match {
        case Some(data) => Success(Telemetry(source("id").asInstanceOf[String], source("ts").asInstanceOf[Long], data))
        case None => Failure(new Exception(s"could not parse 'data': ${source}"))
      }      
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(uri.url)))

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

  def +(telemetry:Telemetry):Try[Telemetry] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${telemetry}"))
  }

  def del(id:ID):Try[ID] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def ?(id:ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry] = {
    log.info(s"id=${id} range=[$ts0,$ts1], op=${op}")
    val r = {
      if(ts0 == 0L && ts1 == Long.MaxValue)
        client.execute { ElasticDsl.search(uri.index).termQuery(("id",id)) }
      else 
        client.execute { ElasticDsl.search(uri.index)
        .rawQuery(s"""
          {
            "bool": {
                "must": [
                    {
                        "terms": {
                            "id": [
                                "${id}"
                            ]
                        }
                    },
                    {
                        "range": {
                            "ts": {
                                "gte": ${ts0},
                                "lte": ${ts1}
                            }
                        }
                    }
                ]
            }
          }
          """)
        }      
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toSeq
  }

  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = {
    search(txt,ts0,ts1)
  }

  def scan(txt:String):Seq[Telemetry] = {
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
    r.result.to[Telemetry].toSeq
  }

  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = {   
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(
        
        //.search(uri.index)
        //.query(txt)
        s"""
        {
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
    r.result.to[Telemetry].toSeq
    
    // r match {
    //   case failure: RequestFailure => List.empty
    //   case results: RequestSuccess[SearchResponse] => r.as[Telemetry] //results.result.hits.hits.toList
    //   case results: RequestSuccess[_] => results.result
    // }
  }

  def grep(txt:String):Seq[Telemetry] = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .query {
          ElasticDsl.wildcardQuery("id",txt)
        }
    }.await

    log.info(s"r=${r}")
    r.result.to[Telemetry].toSeq
  }

  def typing(txt:String):Seq[Telemetry] = {  
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .rawQuery(s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "id", "id._3gram" ] }}
    """)        
    }.await
    
    log.info(s"r=${r}")
    r.result.to[Telemetry].toSeq
  }

  def ???(ts0:Long,ts1:Long,from:Option[Int]=None,size:Option[Int]=None):Telemetrys = {
    val r = client.execute {
      ElasticDsl
        .search(uri.index)
        .from(from.getOrElse(0))
        .size(size.getOrElse(Int.MaxValue))
        .rawQuery(
        s"""
        {
          "bool" : {             
            "must" : {
              "range": {
                "ts": {
                  "gte": ${ts0},
                  "lte": ${ts1}
                }
              }
            }            
          }
        }
        """)
    }.await

    log.debug(s"r=${r}")
    val tt = r.result.to[Telemetry].toSeq

    Telemetrys(tt.drop(from.getOrElse(0)).take(size.getOrElse(Int.MaxValue)),total = Some(tt.size))
  }
}
