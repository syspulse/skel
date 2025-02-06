package io.syspulse.skel.ext

import java.util.concurrent.TimeUnit
import io.syspulse.skel.util.Util
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.crypto.eth.abi3.AbiDef
import io.syspulse.skel.crypto.eth.abi3.Abi

case class Contract(
  contractId:String, 
  address:String, 
  network:String, 
  name:String, 
  addressImpl:Option[String] = None,
  abi:Option[Abi] = None,
  implAbi:Option[Abi] = None,
)

case class Detector(detectorId:String, name:String, did:String)
case class DetectorSchema(schemaId:String, did:String, ver:String)
case class Trigger(triggerId:String, name:String, typ:String)

class ExtClient(baseUrl:String, accessToken0:Option[String] = None) {
  protected val log = Logger(getClass)

  def verToLong(ver:String):Long = {
    ver.split("\\.").map(_.toLong).zip(Seq(100,10,1)).map{ case(v,m) => v * m}.reduce(_ + _)
  }
  
  val accessToken = accessToken0
    .orElse(sys.env.get("ACCESS_TOKEN_ADMIN"))
    .orElse(Option(os.read(os.Path("ACCESS_TOKEN_ADMIN",os.pwd)).trim))
    .orElse(sys.env.get("EXT_PILOT_TOKEN"))
    .orElse(sys.env.get("ACCESS_TOKEN"))
    .getOrElse(throw new RuntimeException("missing accessToken"))

  def getProjectContracts(pid:String, addr:Option[String] = None, name:Option[String] = None):Seq[Contract] = {
    val url = s"${baseUrl}/contract/search"
    val data = 
      if(addr.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid} AND (address='${addr.get.toLowerCase}')"}"""
      else 
      if(name.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid} AND name='${name.get}' "}""" 
      else
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid}"}"""
    
    log.debug(s"data: ${data}")

    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val contracts = json("data").arr.map { c =>
      Contract(
        c("id").num.toLong.toString,
        address = c("address").str, 
        network = c("chainUid").str, 
        name = c("name").str,
        addressImpl = c.obj.get("implementation").flatMap(_.strOpt)
      )
    }
    contracts.toSeq
  }

  def getContractDetectors(cid:String, name:Option[String] = None):Seq[Detector] = {
    val url = s"${baseUrl}/detector/search"
    val data = 
      if(name.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"contractId = ${cid} AND name='${name.get}' "}""" 
      else
        s"""{"from":0,"size":10,"trackTotal":false,"where":"contractId = ${cid}"}"""
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val detectors = json("data").arr.map { d =>
      Detector(
        d("id").num.toLong.toString,
        name = d("name").str,
        did = d("schema").obj("name").str,
      )
    }
    detectors.toSeq
  }

  def addContract(pid:String, addr:String, network:String, name:String):Contract = {
    val url = s"${baseUrl}/contract"
    val data = 
      s"""{"address":"${addr}","chainUid":"${network.toLowerCase}","name":"${name}","projectId":${pid},"addressType":"CONTRACT"}"""
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val contract = 
      Contract(
        json("id").num.toLong.toString,
        json("address").str, 
        json("chainUid").str, 
        json("name").str
      )

    contract
  }

  def delContract(pid:String, addr:Option[String] = None,name:Option[String] = None):Contract = {
    // find contractId

    val contracts = getProjectContracts(pid, addr, name)
    if(contracts.isEmpty)
      throw new RuntimeException(s"contract not found: ${addr.getOrElse(name.get)}")
    val contractId = contracts.head.contractId
    
    val url = s"${baseUrl}/contract/${contractId}"
    
    val rsp = requests.delete(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
    )

    log.info(s"rsp: ${rsp}")
    contracts.head
  }

  def getContract(pid:String, contractId:Int):Contract = {
    
    val url = s"${baseUrl}/contract/${contractId}/withAbi"    
    val rsp = requests.get(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
    )

    log.debug(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
        
    val abi = try {
      Some(Abi(json("abi").toString))
    } catch {
      case e:Exception => 
        log.warn(s"failed to parse ABI: ${contractId}: ${e.getMessage}")
        None
    }

    val implAbi = json.obj.get("implAbi").flatMap(_.strOpt).map(a => Abi(a))

    val addrAbi = (abi,implAbi) match {
      case (Some(abi),Some(implAbi)) => Some(abi.merge(implAbi))
      case (Some(abi),None) => Some(abi)
      case (None,Some(implAbi)) => Some(implAbi)
      case (None,None) => None
    }

    // merge abi

    val contract = Contract(
      json("id").num.toLong.toString,
      json("address").str, 
      json("chainUid").str, 
      json("name").str,
      abi = abi,
      implAbi = implAbi,
    )
    contract
  }

// ----------------------------------------------------------------------------------------------------
  def getDetectorSchemas(did:Option[String] = None):Seq[DetectorSchema] = {
    val url = s"${baseUrl}/schema/search"
    val data = 
      if(did.isDefined) 
        s"""{"size":10000,"trackTotalCount":10000,"trackTotal":false,"where":"status=ACTIVE"}"""
      else
        s"""{"size":10000,"trackTotalCount":10000,"trackTotal":false,"where":"status=ACTIVE"}"""
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.debug(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val ss = json("data").arr.map { d =>
      DetectorSchema(
        schemaId = d("id").num.toLong.toString,
        did = d("name").str,        
        ver = d("version").str,
      )
    }
      .sortBy(s => - verToLong(s.ver))
      .toSeq

    if(did.isDefined)
      ss.filter(_.did == did.get)
    else
      ss
  }

  def severityToDouble(sev:String):Double = {
    sev.toUpperCase match {
      case "CRITICAL" => 0.75
      case "HIGH" => 0.5
      case "MEDIUM" => 0.25
      case "LOW" => 0.1
      case "INFO" => 0.0
      case "AUTO" => -1
      case s => s.toDouble
    }
  }

  def addDetector(pid:String, cid:String, did:String, name:String, tags:Seq[String] = Seq("COMPLIANCE"),sev:String = "AUTO", conf:ujson.Obj = Map.empty):Detector = {
    val url = s"${baseUrl}/detector"
    val src = "ATTACK_DETECTOR"
    val status = "ACTIVE"    

    val schemaId = if(did.head.isDigit)
      did.toInt
    else {
      val d = getDetectorSchemas(Option(did))
      if(d.isEmpty)
        throw new RuntimeException(s"detector schema not found: '${did}'")
      else
        d.head.schemaId
    }
    
    val confData = conf.copy(conf.value += "severity" -> ujson.Num(severityToDouble(sev)))
    val confStr = confData.toString()

    val tagsStr = tags.map(t => s""""${t}"""").mkString(",")
    val data = 
      s"""{"config":${confStr},"source":"${src}","destinations":[],"name":"${name}","status":"${status}","tags":[${tagsStr}],"schemaId":${schemaId},"contractId":${cid}}"""
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val detector = 
      Detector(
        json("id").num.toLong.toString,
        json("name").str,
        did,
      )

    detector
  }

  def delDetector(detectorId:String):Detector = {    
    val url = s"${baseUrl}/detector/${detectorId}"
    
    val rsp = requests.delete(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
    )

    log.info(s"rsp: ${rsp}")
    Detector(detectorId,"","")
  }

  def addTrigger(pid:String, cid:String, typ:String, name:String, sev:String, conf:ujson.Value):Trigger = {
    val url = s"${baseUrl}/detector"
    val status = "ACTIVE"
    val typ = "FAILED_TRANSACTIONS"
    
    val confData = conf //conf.copy(conf.value += "severity" -> ujson.Num(sev))
    val confStr = confData.toString()

    val data = 
      s"""{
        "contractId":"${cid}",
        "type":"${typ}",
        "name":"${name}",
        "config":${confStr},
        "alerts":[
          {
            "severity":"MEDIUM",
            "name":"Failed Transaction",
            "message":"Transaction failed or reverted",
            "status":"${status}",
            "destinations":[]
          }
        ]
      }""".replaceAll("\n","")
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    val trigger = 
      Trigger(
        json("id").num.toLong.toString,
        json("name").str,
        typ,
      )

    trigger
  }

  def delTrigger(triggerId:String, name:Option[String] = None):Trigger = {    
    val url = s"${baseUrl}/trigger/${triggerId}"
    
    val rsp = requests.delete(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
    )

    log.info(s"rsp: ${rsp}")
    Trigger(triggerId,"","")
  }

  def callContract(addr:String, network:String, func:String, params:Seq[String]):String = {
    val pid = "0"
    val url = s"${baseUrl}/tools/common/${pid}/abi/call"
    val paramsJson = params.map(p => s""""${p}"""").mkString(",")
    val data = 
      s"""{"addr":"${addr}","chain":"${network}","func":"${func}","params":[${paramsJson}]}"""
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())
    json("data").str
  }

  case class AmlData(
    address:String,
    chain:String,
    provider:Option[String],
    score:Option[Double],
    source:Option[String],
    tags:Seq[String]
  )

  def getAml(addr:String):AmlData = {
    
    //val url = s"${baseUrl}/contract/${contractId}/withAbi"
    val url = s"https://aml.dev.extractor.live/api/v1/aml/gl/${addr}?meta=report"
    val rsp = requests.get(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
    )

    log.info(s"rsp: ${rsp}")
    
    val json = ujson.read(rsp.text())

    val amlData = AmlData(
      json("addr").str,
      json("chain").str,
      json.obj.get("oid").flatMap(_.strOpt),
      score = json.obj.get("score").flatMap(_.numOpt),
      source = json("meta")("owner").obj.get("data").flatMap(_.strOpt),
      tags = json("tags").arr.map(_.str).toSeq,
    )
    amlData
  }

  def alert(
    cid:Int, 
    did:String,        
    typ:String,
    sid:String = "ext", 
    eid:String = UUID.randomUUID().toString, 
    cat:String = "ALERT", 
    sev:Double = 0.1, 
    addr:Option[String] = None, 
    network:String = "anvil", 
    meta:Map[String,String] = Map.empty):String = {
    
    val pid = "0"
    val url = s"${baseUrl}/event"
    
    val ts = System.currentTimeMillis()
    
    val metaStr = if(meta.size > 0) 
      ",\n" + meta.map(m => s""""${m._1}": "${m._2}"""").mkString(",")
    else
      ""

    val data = s"""
{
  "events": [
    {
      "ts": ${ts},
      "cid": ${cid},
      "did": "${did}",
      "sid": "${sid}",
      "eid": "${eid}",
      "type": "${typ}",
      "category": "${cat}",
      "severity": ${sev},
      "blockchain": {
        "chain_id": "31337",
        "network": "${network}"
      },
      "metadata": {
         "monitored_contract": "${addr}"         
         ${metaStr}
      }
    }
  ]
}""".replaceAll("\n","")
    
    val rsp = requests.post(
      url,
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${accessToken}"
      ),
      data = data,      
    )

    log.info(s"rsp: ${rsp}")    
    rsp.text()
  }
}
