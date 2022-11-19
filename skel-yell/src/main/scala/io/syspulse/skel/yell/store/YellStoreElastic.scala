package io.syspulse.skel.yell.store

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

import io.syspulse.skel.yell._
import io.syspulse.skel.yell.Yell.ID

import io.syspulse.skel.yell.YellScan

class YellStoreElastic(elasticUri:String,elacticIndex:String) extends YellStore {  
  private val log = Logger(s"${this}")

  implicit object VideoHitReader extends HitReader[Yell] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Yell] = {
      val source = hit.sourceAsMap
      Success(Yell(source("vid").asInstanceOf[Long], source("vid").asInstanceOf[Int], source("area").asInstanceOf[String], source("text").asInstanceOf[String]))
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(elasticUri)))

  import ElasticDsl._  
  def all:Seq[Yell] = {    
    val r = client.execute {
      ElasticDsl
      .search(elacticIndex)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Yell].toList
  }

  // slow and memory hungry !
  def size:Long = {
    val r = client.execute {
      ElasticDsl.count(Indexes(elacticIndex))
    }.await
    r.result.count
  }

  def +(yell:Yell):Try[YellStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def del(id:ID):Try[YellStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def -(yell:Yell):Try[YellStore] = {     
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def ?(id:ID):Option[Yell] = {
    search(id.toString).headOption
  }

  def ??(txt:String):List[Yell] = {
    search(txt)
  }

  def scan(txt:String):List[Yell] = {
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .rawQuery(s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["area", "text"]
      }
    }
    """)        
    }.await

    log.info(s"r=${r}")
    r.result.to[Yell].toList
  }

  def search(txt:String):List[Yell] = {   
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .search(elacticIndex)
        .query(txt)
    }.await

    log.info(s"r=${r}")
    r.result.to[Yell].toList
  }

  def grep(txt:String):List[Yell] = {
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .query {
          ElasticDsl.wildcardQuery("text",txt)
        }
    }.await

    log.info(s"r=${r}")
    r.result.to[Yell].toList
  }

  def typing(txt:String):List[Yell] = {  
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .rawQuery(s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "text._3gram" ] }}
    """)        
    }.await
    
    log.info(s"r=${r}")
    r.result.to[Yell].toList
  }
}
