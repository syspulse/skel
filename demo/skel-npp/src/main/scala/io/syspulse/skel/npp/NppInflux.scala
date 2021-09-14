package io.syspulse.skel.npp

import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

import upickle._
import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.annotations.{Column, Measurement}
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.query.FluxRecord
import com.influxdb.client.scala.InfluxDBClientScalaFactory
import com.influxdb.client.domain.WritePrecision

class NppInflux(influxUri:String,influxOrg:String,influxBucket:String,influxToken:String) 
  extends Stage[NppData]("NPP-Influx") {

  log.info(s"${influxUri}:${influxOrg}:${influxBucket}:${influxToken}")
  
  val influxDBClient = getInfluxDB
  val writeApi = influxDBClient.makeWriteApi
  val queryApi = influxDBClient.getQueryApi

  def getInfluxDB = {
    val influxOptions = InfluxDBClientOptions.builder()
      .url(influxUri)
      .authenticateToken(influxToken.toCharArray())
      .org(influxOrg)
      .bucket(influxBucket)
      .build();
    
    InfluxDBClientFactory.create(influxOptions)
  }

  def exec(flow:Flow[NppData]): Flow[NppData] = {
    flow.data.radiation.foreach{ r => 
      try {
        val nir = new NppInfluxRecord(r.ts,r.area,r.geohash,r.dose)
        writeApi.writeMeasurement(WritePrecision.MS, nir)
        
      } catch {
        case e:Exception => log.error(s"failed to save: ${r}",e); 
      }
    }

    flow
  }
}