package io.syspulse.skel.video.store

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

import io.syspulse.skel.video._
import io.syspulse.skel.video.Video.ID

import io.syspulse.skel.video._
import io.syspulse.skel.video.elastic.VideoScan
import io.syspulse.skel.video.elastic.VideoSearch

class VideoStoreElastic(elasticUri:String,elacticIndex:String) extends VideoStore {
  private val log = Logger(s"${this}")

  implicit object VideoHitReader extends HitReader[Video] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Video] = {
      val source = hit.sourceAsMap
      Success(Video(VID.fromElastic(source("vid").asInstanceOf[Map[String,String]]), source("title").toString))
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(elasticUri)))

  import ElasticDsl._  
  def all:Seq[Video] = {    
    val r = client.execute {
      ElasticDsl
      .search(elacticIndex)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Video].toList
  }

  // slow and memory hungry !
  def size:Long = {
    val r = client.execute {
      ElasticDsl.count(Indexes(elacticIndex))
    }.await
    r.result.count
  }

  def +(video:Video):Try[Video] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${video}"))
  }

  def del(id:ID):Try[ID] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def ?(vid:VID):Try[Video] = {
    search(vid.toString).take(1).headOption match {
      case Some(o) => Success(o)
      case None => Failure(new Exception(s"not found: ${vid}"))
    }
  }

  def ??(txt:String):List[Video] = {
    search(txt)
  }

  def scan(txt:String):List[Video] = {
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .rawQuery(s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["title", "vid"]
      }
    }
    """)        
    }.await

    log.info(s"r=${r}")
    r.result.to[Video].toList
  }

  def search(txt:String):List[Video] = {   
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .search(elacticIndex)
        .query(txt)
    }.await

    log.info(s"r=${r}")
    r.result.to[Video].toList
    
    // r match {
    //   case failure: RequestFailure => List.empty
    //   case results: RequestSuccess[SearchResponse] => r.as[Video] //results.result.hits.hits.toList
    //   case results: RequestSuccess[_] => results.result
    // }
  }

  def grep(txt:String):List[Video] = {
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .query {
          ElasticDsl.wildcardQuery("title",txt)
        }
    }.await

    log.info(s"r=${r}")
    r.result.to[Video].toList
  }

  def typing(txt:String):List[Video] = {  
    val r = client.execute {
      ElasticDsl
        .search(elacticIndex)
        .rawQuery(s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "title", "title._3gram" ] }}
    """)        
    }.await
    
    log.info(s"r=${r}")
    r.result.to[Video].toList
  }
}
