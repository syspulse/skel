package io.syspulse.skel.elastic

import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.ElasticsearchParams
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSource

trait ElasticSearch[T] extends ElasticClient[T] {
  
  def getWildcards(txt:String):String

  def getSearches(txt:String):String

  def getSearch(txt:String):String

  def grep(txt:String):Seq[T] = search(txt,getWildcards(txt))

  def searches(txt:String):Seq[T] = search(txt,getSearches(txt))

  def search(txt:String):Seq[T] = search(txt,getSearch(txt))

  def search(txt:String,q:String):Seq[T] = {
    val result = ElasticsearchSource.typed[T](
    //val result = ElasticsearchSource(
        ElasticsearchParams.V7(getIndexName()),
        settings = getSourceSettings(),
        query = q,
        // searchParams = Map(
        //   "_source" -> """ ["vid", "ts", "title", "category"] """
        // )
      )
      .map { m =>
        m.source
      }
      .runWith(Sink.seq)

    val r = Await.result(result, timeout())
    log.info(s"result: ${r.mkString("\n")}")
    r
  }

  //override def connect(elasticUri:String,elasticIndex:String):ElasticSearch[T] = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticSearch[T]]
}