package io.syspulse.skel.telemetry.store

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Failure

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global 

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.uri.DynamoURI
import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.ingest.dynamo.DynamoFormat

import io.syspulse.skel.telemetry.Telemetry.ID

object TelemetryDynamoFormat extends DynamoFormat[Telemetry] {
  
  def toDynamo(o:Telemetry) = Map(
    "ID" -> AttributeValue.builder.s(o.id.toString).build(),
    "TS" -> AttributeValue.builder.n(o.ts.toString).build(),
    "DATA" -> AttributeValue.builder.ns(o.data.map(_.toString).asJava).build(),
  )

  def fromDynamo(m:Map[String,AttributeValue]) = Telemetry(
    id = m.get("ID").map(_.s()).getOrElse(""),
    ts = m.get("TS").map(_.n()).getOrElse("0").toLong,
    data = m.get("DATA").map(_.ns().asScala).getOrElse(List()).asInstanceOf[List[String]]
  )
}

class TelemetryStoreDynamo(dynamoUri:DynamoURI) extends DynamoClient(dynamoUri) with TelemetryStore {
  
  def all:Seq[Telemetry] = Seq()
  def size:Long = 0
  def +(t:Telemetry):Try[TelemetryStore] = Failure(new UnsupportedOperationException)
  def del(id:Telemetry.ID):Try[TelemetryStore] = Failure(new UnsupportedOperationException)
  def -(telemetry:Telemetry):Try[TelemetryStore] = Failure(new UnsupportedOperationException)
  def ?(id:ID,ts0:Long,ts1:Long):Seq[Telemetry] = query(id).toSeq
  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = Seq()
  def scan(txt:String):Seq[Telemetry] = scan().toSeq
  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = Seq()

  def get(key:String) = {
    log.info(s"key=${key}")
    val req = GetItemRequest
          .builder()
          .key(
            Map(
              "VID"-> AttributeValue.builder.s(key).build(),
              "TS" -> AttributeValue.builder.n("0".toString).build(),
            ).asJava
          )
          .tableName(getTable())
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    
    if(r.hasItem())
      Some(TelemetryDynamoFormat.fromDynamo(r.item().asScala.toMap))
    else
      None
  }

  def query(q:String) = {
    log.info(s"q=${q}")
    val req = QueryRequest
          .builder()
          .tableName(getTable())
          .keyConditionExpression("VID = :vid")
          .expressionAttributeValues(Map(":vid" -> AttributeValue.builder().s(q).build()).asJava)
          //.attributesToGet("VID","TS","TITLE")
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    
    r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap))
  }

  def scan(limit:Int = -1) = {
    log.info(s"limit=${limit}")
    val req = ScanRequest
          .builder()
          .tableName(getTable())
          .limit(if(limit == -1) Int.MaxValue else limit)
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    
    r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap))    
  }

}