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

import spray.json.JsonFormat
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

trait ElasticScan[T] extends ElasticClient[T] {
    
  def getSearchParamas():Map[String,String]
  
  def scan(txt:String):Seq[T] = {
    val result = ElasticsearchSource.typed[T](
    //val result = ElasticsearchSource(
        ElasticsearchParams.V7(getIndexName()),
        settings = getSourceSettings(),
        searchParams = getSearchParamas()
      )
      .map { m =>
        m.source
      }
      .runWith(Sink.seq)

    val r = Await.result(result, timeout())
    log.info(s"result: ${r.mkString("\n")}")
    //r.map(r => convertTo(r))
    r
  }

  //override def connect(elasticUri:String,elasticIndex:String):ElasticScan[T] = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticScan[T]]
}