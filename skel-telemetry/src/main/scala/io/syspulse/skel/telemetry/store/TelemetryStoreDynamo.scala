package io.syspulse.skel.telemetry.store

import scala.jdk.CollectionConverters._
import scala.util.{Success,Failure,Try}

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
import io.syspulse.skel.uri.DynamoURI
import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.ingest.dynamo.DynamoFormat

import io.syspulse.skel.telemetry.Telemetry.ID
import io.syspulse.skel.telemetry.server.Telemetrys

object TelemetryDynamoFormat extends DynamoFormat[Telemetry] {
  
  def toDynamo(o:Telemetry) = Map(
    "ID" -> AttributeValue.builder.s(o.id.toString).build(),
    "TS" -> AttributeValue.builder.n(o.ts.toString).build(),
    "DATA" -> AttributeValue.builder.ns(o.data.map(_.toString).asJava).build(),
  )

  def fromDynamo(m:Map[String,AttributeValue]) = Telemetry(
    id = m.get("ID").map(_.s()).getOrElse(""),
    ts = m.get("TS").map(_.n()).getOrElse("0").toLong,
    v = m.get("DATA").map(_.ns().asScala.toList).getOrElse(List()).asInstanceOf[List[String]]
  )
}

case class DynamoData(d:List[Any])

class TelemetryStoreDynamo(dynamoUri:DynamoURI) extends DynamoClient(dynamoUri) with TelemetryStore {
  val timeout:Duration = Duration("5 seconds")

  def clean():Try[TelemetryStore] = { Failure(new UnsupportedOperationException) }
  def all:Seq[Telemetry] = scan().toSeq
  def size:Long = scan().size
  def +(t:Telemetry):Try[Telemetry] = {
    log.info(s"t=${t}")
    val req = PutItemRequest
          .builder()
          .item(
            Map(
              "ID"-> AttributeValue.builder.s(t.id).build(),
              "TS" -> AttributeValue.builder.n(t.ts.toString).build(),
              "DATA" -> AttributeValue.builder.s(Util.toCSV(DynamoData(t.data))).build(),
            ).asJava
          )
          .tableName(getTable())
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, timeout)
    log.info(s"t=${t}: ")
    Success(t)
  }

  def del(id:Telemetry.ID):Try[Telemetry.ID] = Failure(new UnsupportedOperationException)
  def ?(id:ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry] = range(id,ts0,ts1).toSeq
  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = range(txt,ts0,ts1).toSeq
  def scan(txt:String):Seq[Telemetry] = scan().toSeq
  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = range(txt,ts0,ts1).toSeq

  def get(id:String,ts:Long) = {
    log.info(s"id=${id},ts=${ts}")
    val req = GetItemRequest
          .builder()
          .key(
            Map(
              "ID"-> AttributeValue.builder.s(id).build(),
              "TS" -> AttributeValue.builder.n(ts.toString).build(),
            ).asJava
          )
          .tableName(getTable())
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, timeout)
    
    if(r.hasItem())
      Some(TelemetryDynamoFormat.fromDynamo(r.item().asScala.toMap))
    else
      None
  }

  def range(id:String,ts0:Long,ts1:Long,limit:Int = 1000) = {
    val req = QueryRequest
          .builder()
          .tableName(getTable())
          .keyConditionExpression("ID = :id and TS BETWEEN :ts0 AND :ts1 ")
          //.withKeyConditionExpression("#ID = :id and #TS BETWEEN :ts0 AND :ts1 ")
          .expressionAttributeValues(
            Map(
              ":id" -> AttributeValue.builder().s(id).build(),
              ":ts0" -> AttributeValue.builder().n(ts0.toString).build(),
              ":ts1" -> AttributeValue.builder().n(ts1.toString).build()
            ).asJava
          )
          .limit(limit)
          //.attributesToGet("ID","TS","DATA")
          .build()

    log.info(s"req=${req}")
          
    val result = DynamoDb.single(req)
    val r = Await.result(result, timeout)
    r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap))
  }

  def ???(ts0:Long,ts1:Long,from:Option[Int]=None,size:Option[Int]=None):Telemetrys = {
    val req = QueryRequest
          .builder()
          .tableName(getTable())
          .keyConditionExpression("TS BETWEEN :ts0 AND :ts1 ")
          //.withKeyConditionExpression("#ID = :id and #TS BETWEEN :ts0 AND :ts1 ")
          .expressionAttributeValues(
            Map(
              ":ts0" -> AttributeValue.builder().n(ts0.toString).build(),
              ":ts1" -> AttributeValue.builder().n(ts1.toString).build()
            ).asJava
          )
          .limit(from.getOrElse(0) + size.getOrElse(Int.MaxValue))
          //.attributesToGet("ID","TS","DATA")
          .build()

    log.info(s"req=${req}")
          
    val result = DynamoDb.single(req)
    val r = Await.result(result, timeout)
    val tt = r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap)).toSeq
    
    Telemetrys(tt.drop(from.getOrElse(0)).take(size.getOrElse(Int.MaxValue)),total = Some(tt.size))
  }

  override def last(id:String):Try[Telemetry] = {
    val req = QueryRequest
          .builder()
          .tableName(getTable())          
          .keyConditionExpression("ID = :id")
          .expressionAttributeValues(
            Map(
              ":id" -> AttributeValue.builder().s(id).build(),
            ).asJava
          )
          .scanIndexForward(false)          
          .limit(1)
          .build()

    log.info(s"req=${req}")
          
    val result = DynamoDb.single(req)
    val r = Await.result(result, timeout)
    r.items().asScala.take(1).map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap)).headOption match {
      case Some(o) => Success(o)
      case None => Failure(new Exception(s"not found: ${id}"))
    }    
  }

  // returns the latest inserted element !  
  def latest(id:String):Option[Telemetry] = {
    val req = QueryRequest
          .builder()
          .tableName(getTable())          
          .keyConditionExpression("ID = :id")
          .expressionAttributeValues(
            Map(
              ":id" -> AttributeValue.builder().s(id).build(),
            ).asJava
          )
          .scanIndexForward(true)
          .limit(1)
          //.attributesToGet("ID","TS","DATA")
          .build()

    log.info(s"req=${req}")
          
    val result = DynamoDb.single(req)
    val r = Await.result(result, timeout)
    r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap)).headOption
  }

  def scan(limit:Int = -1) = {
    log.info(s"limit=${limit}")
    val req = ScanRequest
          .builder()
          .tableName(getTable())
          .limit(if(limit == -1) Int.MaxValue else limit)
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, timeout)
    
    r.items().asScala.map( r => TelemetryDynamoFormat.fromDynamo(r.asScala.toMap))    
  }
}