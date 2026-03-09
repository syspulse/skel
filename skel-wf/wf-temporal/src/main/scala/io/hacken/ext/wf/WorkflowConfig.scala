package io.hacken.ext.wf

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.Logger

import spray.json._
import io.syspulse.skel.service.JsonCommon
import java.util.concurrent.TimeUnit

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class WorkflowConfigSchema(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, //"ACTIVE/DISABLED",
  name: String, 
  version: String, //"0.2.7",
  schema: Option[JsObject]
)

case class WorkflowConfig(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, // ACTIVE, DISABLED, DELETED 
  
  schema: Option[WorkflowConfigSchema],
  
  name: String, // user supplied name
  source: String, 
  tags: Seq[String], 
  config: Option[JsObject],  // configuration according to schema

) extends Ingestable

object DetectorConfigJson extends JsonCommon { 
  implicit val jf_wf_sch = jsonFormat7(WorkflowConfigSchema)  
  implicit val jf_wf_cfg = jsonFormat9(WorkflowConfig.apply _)
}

object WorkflowConfig {
  
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

  def getDouble(conf:WorkflowConfig,key:String,default:Double):Double = {
    conf.config.flatMap(c => getDouble(c.fields.get(key))).getOrElse(default)
  }
  def getBoolean(conf:WorkflowConfig,key:String,default:Boolean):Boolean = {
    conf.config.flatMap(c => getBoolean(c.fields.get(key))).getOrElse(default)
  }
  def getInt(conf:WorkflowConfig,key:String,default:Int):Int = {
    conf.config.flatMap(c => getInt(c.fields.get(key))).getOrElse(default)
  }
  def getLong(conf:WorkflowConfig,key:String,default:Long):Long = {
    conf.config.flatMap(c => getLong(c.fields.get(key))).getOrElse(default)
  }
  def getString(conf:WorkflowConfig,key:String,default:String):String = {
    conf.config.flatMap(c => getString(c.fields.get(key))).getOrElse(default)
  }
  def getBigInt(conf:WorkflowConfig,key:String,default:BigInt):BigInt = {
    conf.config.flatMap(c => getBigInt(c.fields.get(key))).getOrElse(default)
  }

  def getDouble(conf:WorkflowConfig,key:String):Option[Double] = {
    conf.config.flatMap(c => getDouble(c.fields.get(key)))
  }
  def getBoolean(conf:WorkflowConfig,key:String):Option[Boolean] = {
    conf.config.flatMap(c => getBoolean(c.fields.get(key)))
  }
  def getLong(conf:WorkflowConfig,key:String):Option[Long] = {
    conf.config.flatMap(c => getLong(c.fields.get(key)))
  }
  def getInt(conf:WorkflowConfig,key:String):Option[Int] = {
    conf.config.flatMap(c => getInt(c.fields.get(key)))
  }
  def getString(conf:WorkflowConfig,key:String):Option[String] = {
    conf.config.flatMap(c => getString(c.fields.get(key)))
  }
  def getBigInt(conf:WorkflowConfig,key:String):Option[BigInt] = {
    conf.config.flatMap(c => getBigInt(c.fields.get(key)))
  }
  
  def getMap(conf:WorkflowConfig,key:String,default:Map[String,Any]=Map.empty):Map[String,Any] = {
    conf.config.flatMap(c => {
      getMap(c.fields.get(key))
    }).getOrElse(default)
  }

  def getArrayMap(conf:WorkflowConfig,key:String,default:Vector[Map[String,Any]] = Vector.empty):Vector[Map[String,Any]] = {
    conf.config.flatMap(c => {      
      getArrayMap(c.fields.get(key))
    }).getOrElse(default)
  }

  def getStringMap(conf:WorkflowConfig,key:String,default:Map[String,String] = Map.empty):Map[String,String] = {
    val data = getString(conf,key)
    Util.getStringMap(data,default)
  }
  
}
