package io.syspulse.skel.tag.store

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

import io.syspulse.skel.tag._

class TagStoreElastic(elasticUri:String,elacticIndex:String) extends TagStore {
  private val log = Logger(s"${this}")

  implicit object TagHitReader extends HitReader[Tag] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Tag] = {
      val source = hit.sourceAsMap
      Success(Tag(source("id").toString, source("tags").asInstanceOf[List[String]]))
    }
  }
  
  val client = ElasticClient(JavaClient(ElasticProperties(elasticUri)))

  import ElasticDsl._  
  def all:Seq[Tag] = {    
    val r = client.execute {
      ElasticDsl
      .search(elacticIndex)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList
  }

  override def limit(from:Option[Int]=None,size:Option[Int]=None):Seq[Tag] = {    
    val r = client.execute {
      ElasticDsl
      .search(elacticIndex)
      .from(from.getOrElse(0))
      .size(size.getOrElse(10))
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList
  }

  // slow and memory hungry !
  def size:Long = {
    val r = client.execute {
      ElasticDsl.count(Indexes(elacticIndex))
    }.await
    r.result.count
  }

  def +(tag:Tag):Try[TagStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${tag}"))
  }

  def ?(id:String):Try[Tag] = {
    log.info(s"id=${id}")
    val r = {
     client.execute { ElasticDsl.search(elacticIndex).termQuery(("id",id)) }      
    }.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList match {
      case t :: _ => Success(t)
      case _ => Failure(new Exception(s"not found: ${id}"))
    }
  }

  def ??(txt:String,from:Option[Int],size:Option[Int]):List[Tag] = {   
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .search(elacticIndex)
        .from(from.getOrElse(0))
        .size(size.getOrElse(10))
        .query(txt)
        //.matchQuery("_all",txt)
        //.operator(MatchQueryBuilder.Operator.AND)
        .sortByFieldDesc("score")
    }.await

    // Multiple words require fixing ElasticSearch Java lib dependency
    // import org.elasticsearch.index.query.MatchQueryBuilder
    // val r = client.execute {
    //   com.sksamuel.elastic4s.ElasticDsl
    //     .search(elacticIndex)
    //     .query {
    //       matchQuery("_all",txt).operator(MatchQueryBuilder.Operator.AND)
    //     }
    //     .sortByFieldDesc("score")
    // }.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList
  }

  def del(id:String):Try[TagStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }
}
