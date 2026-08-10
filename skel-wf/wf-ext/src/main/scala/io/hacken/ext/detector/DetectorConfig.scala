package io.hacken.ext.detector

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.Logger

import spray.json._
import io.syspulse.skel.service.JsonCommon
import java.util.concurrent.TimeUnit

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class DetectorConfigContract(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  projectId: Int,
  tenantId: Int,
  chainUid: Option[String],
  proxyAddress: Option[String],     // depereacted
  implementation: Option[String],   // implementation address
  address: Option[String],         // address can be empty now
  name: String
)

case class DetectorConfigDestination(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, //"ACTIVE",
  `type`: String, // "EMAIL",
  params: Map[String,String],
  destinationId: Int
)

case class DetectorConfigSchema(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, //"ACTIVE/DISABLED",
  name: String, // did == Uniqie Detector,
  version: String, //"0.2.7",
  schema: Option[JsObject]
)

case class DetectorConfig(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, // ACTIVE/DISABLED
  contract: DetectorConfigContract, // oid -> contract.tenantId; pid -> contract.projectId
  
  schema: Option[DetectorConfigSchema],
  
  name: String, // user supplied name
  source: String, // "ATTACK_DETECTOR",
  tags: Seq[String], // "SECURITY"
  config: Option[JsObject],  // configuration according to schema
  destinations: Seq[DetectorConfigDestination],
  //actions: []

  meta: Option[Map[String,String]] = None  // transient runtime metadata (e.g. "activity_id" from /resolve); NOT persisted

) extends Ingestable

object DetectorConfigJson extends JsonCommon { 
  implicit val jf_dc_sch = jsonFormat7(DetectorConfigSchema)  
  implicit val jf_dc_con = jsonFormat10(DetectorConfigContract)
  implicit val jf_dc_dest = jsonFormat7(DetectorConfigDestination)  
  implicit val jf_dc = jsonFormat12(DetectorConfig.apply _)
}


object DetectorConfig {

  val ADDR_ALL = "0x00000000000000000000000000"

  // ATTENTION: `proxyAddress` is deprecated !
  def getAddr(conf:DetectorConfig) = conf.contract.address.map(_.toLowerCase)
    // conf.contract.address match {
    //   case Some(addr) => Some(addr)
    //   case None => ""
    // }
  
  def getBoolean(v:Option[JsValue]):Option[Boolean] = {
    v.map(v => v match {
      case JsBoolean(b) => b
      case _ => v.toString.toBoolean
    })
  }

  def getDouble(v:Option[JsValue]):Option[Double] = {
    v.map(v => v.toString().toDouble)
  }

  def getLong(v:Option[JsValue]):Option[Long] = {
    v.map(v => v.toString().toLong)
  }

  def getInt(v:Option[JsValue]):Option[Int] = {
    v.map(v => v.toString().toInt)
  }

  def getString(v:Option[JsValue]):Option[String] = {
    v.map(v => v match {
      case JsString(s) => s
      case _ => v.toString
    })
  }

  def getBigInt(v:Option[JsValue]):Option[BigInt] = {
    v.flatMap(v => {
      v match {
        case JsString(s) if(s.isEmpty) => None
        case JsString(s) => Some(BigInt(s))
        case JsNumber(n) => Some(BigInt(n.toString()))
        case _ => Some(BigInt(v.toString()))
      }
    })
  }

  def getMap(v:Option[JsValue]):Option[Map[String,Any]] = {    
    v.map(v => v match {
      case JsObject(obj) => obj.map{ case(k,jsValue) => {
        k -> getValue(jsValue)
      }}
      case _ => 
        Map.empty
    })
  }

  def getValue(v:JsValue):Any ={
    v match {
      case v:JsString => v.value
      case _ => v.toString()
    }
  }

  def getArrayMap(v:Option[JsValue]):Option[Vector[Map[String,Any]]] = {    
    v.map(v => v match {
      case JsArray(obj) => obj.map(v => getMap(Some(v)).get)
      case _ => 
        Vector.empty
    })
  }

  def getDouble(conf:DetectorConfig,key:String,default:Double):Double = {
    conf.config.flatMap(c => getDouble(c.fields.get(key))).getOrElse(default)
  }
  def getBoolean(conf:DetectorConfig,key:String,default:Boolean):Boolean = {
    conf.config.flatMap(c => getBoolean(c.fields.get(key))).getOrElse(default)
  }
  def getInt(conf:DetectorConfig,key:String,default:Int):Int = {
    conf.config.flatMap(c => getInt(c.fields.get(key))).getOrElse(default)
  }
  def getLong(conf:DetectorConfig,key:String,default:Long):Long = {
    conf.config.flatMap(c => getLong(c.fields.get(key))).getOrElse(default)
  }
  def getString(conf:DetectorConfig,key:String,default:String):String = {
    conf.config.flatMap(c => getString(c.fields.get(key))).getOrElse(default)
  }
  def getBigInt(conf:DetectorConfig,key:String,default:BigInt):BigInt = {
    conf.config.flatMap(c => getBigInt(c.fields.get(key))).getOrElse(default)
  }

  def getDouble(conf:DetectorConfig,key:String):Option[Double] = {
    conf.config.flatMap(c => getDouble(c.fields.get(key)))
  }
  def getBoolean(conf:DetectorConfig,key:String):Option[Boolean] = {
    conf.config.flatMap(c => getBoolean(c.fields.get(key)))
  }
  def getLong(conf:DetectorConfig,key:String):Option[Long] = {
    conf.config.flatMap(c => getLong(c.fields.get(key)))
  }
  def getInt(conf:DetectorConfig,key:String):Option[Int] = {
    conf.config.flatMap(c => getInt(c.fields.get(key)))
  }
  def getString(conf:DetectorConfig,key:String):Option[String] = {
    conf.config.flatMap(c => getString(c.fields.get(key)))
  }
  def getBigInt(conf:DetectorConfig,key:String):Option[BigInt] = {
    conf.config.flatMap(c => getBigInt(c.fields.get(key)))
  }

  def getSeverity(conf:DetectorConfig,key:String = "severity",default:Double = 0.5):Double = {
    val s = getDouble(conf,key,default) 
    if( s < 0.0 ) default else s
  }

  def getMap(conf:DetectorConfig,key:String,default:Map[String,Any]=Map.empty):Map[String,Any] = {
    conf.config.flatMap(c => {
      getMap(c.fields.get(key))
    }).getOrElse(default)
  }

  def getArrayMap(conf:DetectorConfig,key:String,default:Vector[Map[String,Any]] = Vector.empty):Vector[Map[String,Any]] = {
    conf.config.flatMap(c => {      
      getArrayMap(c.fields.get(key))
    }).getOrElse(default)
  }

  def getStringMap(conf:DetectorConfig,key:String,default:Map[String,String] = Map.empty):Map[String,String] = {
    val data = getString(conf,key)
    Util.getStringMap(data,default)
  }
  
}
