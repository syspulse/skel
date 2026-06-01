package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.{Future, ExecutionContext}
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

  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit object TagHitReader extends HitReader[Tag] {
    // becasue of VID case class, it is converted unmarchsalled as Map from Elastic (field vid.id)
    override def read(hit: Hit): Try[Tag] = {
      val source = hit.sourceAsMap
      Success(Tag(
        source("id").toString,
        source("ts").asInstanceOf[Long],
        source("cat").asInstanceOf[String],
        source("tags").asInstanceOf[List[String]],
        source("score").asInstanceOf[Option[Long]],
        source("sid").asInstanceOf[Option[Long]],
      ))
    }
  }

  val client = ElasticClient(JavaClient(ElasticProperties(elasticUri)))

  import ElasticDsl._
  def all:Future[Seq[Tag]] = Future {
    val r = client.execute {
      ElasticDsl
      .search(elacticIndex)
      .matchAllQuery()
    }.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList
  }

  override def all(from:Option[Int]=None,size:Option[Int]=None):Future[Seq[Tag]] = Future {
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
  def size:Future[Long] = Future {
    val r = client.execute {
      ElasticDsl.count(Indexes(elacticIndex))
    }.await
    r.result.count
  }

  def +(tag:Tag):Future[Tag] = Future {
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .indexInto(elacticIndex)
        .fields("id" -> tag.id)
        .fields("ts" -> tag.ts)
        .fields("cat" -> tag.cat)
        .fields("tags" -> tag.tags)
        .fields("score" -> tag.score.getOrElse(-1))
        .fields("sid" -> tag.sid.getOrElse(-1))
        .refresh(RefreshPolicy.Immediate)
    }.await

    log.info(s"r=${r}")

    if (r.isError)
      throw new Exception(s"could not insert: ${tag}: ${r.error}")
    else
      tag
  }

  def ?(id:String):Future[Tag] = Future {
    log.info(s"id=${id}")
    val r = { client.execute {
      ElasticDsl
        .search(elacticIndex)
        .termQuery(("id",id))
    }}.await

    log.info(s"r=${r}")
    r.result.to[Tag].toList match {
      case t :: _ => t
      case _ => throw new Exception(s"not found: ${id}")
    }
  }

  override def ??(ids:Seq[String])(implicit ec:ExecutionContext):Future[Seq[Tag]] = Future {
    log.info(s"ids=${ids}")
    val r = { client.execute {
      multi {
        ids.map(id => ElasticDsl.search(elacticIndex).termQuery(("id",ids)))
      }
    }}.await

    log.info(s"r=${r}")
    r.result.to[Tag].toSeq
  }

  def find(attr:String,v:Any,from:Option[Int],size:Option[Int]):Tags = {
    log.info(s"attr=(${attr},${v})")
    val r = { client.execute {
      ElasticDsl
        .search(elacticIndex)
        .termQuery((attr,v))
        .from(from.getOrElse(0))
        .size(size.getOrElse(10))
    }}.await

    log.info(s"r=${r}")
    Tags(r.result.to[Tag].toList,total = Some(r.result.hits.total.value))
  }

  def !(id:String,cat:Option[String],tags:Option[Seq[String]]):Future[Tag] = {
    log.info(s"id=${id}, cat=${cat}, tags=${tags}")

    ?(id).flatMap{ t =>
      val r = { client.execute {
        ElasticDsl
          .updateById(elacticIndex,id)
          .doc(
            {if(cat.isDefined) Seq(("cat",cat.get)) else Seq()} ++
            {if(tags.isDefined) Seq(("tags",tags.get)) else Seq()}
          )
      }}.await

      log.info(s"r=${r}")
      r.toEither match {
        case Left(e) => Future.failed(e.asException)
        case Right(_) => ?(id)
      }
    }
  }

  def typing(txt:String,from:Option[Int],size:Option[Int]):Tags =
    ???(txt,None,from,size)

  def search(txt:String,from:Option[Int],size:Option[Int]):Tags =
    ???(txt,None,from,size)

  def ???(terms:String,cat:Option[String],from:Option[Int],size:Option[Int]):Tags = {
    val r = client.execute {
      com.sksamuel.elastic4s.ElasticDsl
        .search(elacticIndex)
        .from(from.getOrElse(0))
        .size(size.getOrElse(10))
        .rawQuery(
          s"""{
            "bool": {
              "must": [
                { "match": { "tags":   "${terms}" }}
              ],
              "filter": [
                { "term":  { "cat": "${cat.getOrElse("")}" }}
              ]
            }
          }
          """
        )
        //.query(terms)
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

    Tags(r.result.to[Tag].toList,total = Some(r.result.hits.total.value))
  }

  def del(id:String):Future[String] = Future {
    val r = client.execute {
      ElasticDsl
        .deleteById(elacticIndex, id)
    }.await

    log.info(s"r=${r}")
    if (r.isError)
      throw new Exception(s"could not delete: ${id}: ${r.error}")
    else
      id
  }
}
