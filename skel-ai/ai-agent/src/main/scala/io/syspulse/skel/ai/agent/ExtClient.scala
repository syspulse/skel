package io.syspulse.skel.ai.agent

import java.util.concurrent.TimeUnit
import io.syspulse.skel.util.Util
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

case class Contract(contractId:String, address:String, network:String, name:String, addressImpl:Option[String] = None)
case class Detector(detectorId:String, name:String, did:String)
case class DetectorSchema(schemaId:String, did:String)
case class Trigger(triggerId:String, name:String, typ:String)

class ExtClient(baseUrl:String, accessToken0:Option[String] = None) {
  protected val log = Logger(getClass)
  
  val accessToken = accessToken0
    .orElse(sys.env.get("ACCESS_TOKEN_ADMIN"))
    .orElse(Option(os.read(os.Path("ACCESS_TOKEN_ADMIN",os.pwd)).trim))
    .orElse(sys.env.get("EXT_PILOT_TOKEN"))
    .getOrElse(throw new RuntimeException("missing accessToken"))

  def getProjectContracts(pid:String, addr:Option[String] = None, name:Option[String] = None):Seq[Contract] = {
    val url = s"${baseUrl}/contract/search"
    val data = 
      if(addr.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid} AND (address='${addr.get}' OR proxyAddress='${addr.get}')"}"""
      else 
      if(name.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid} AND name='${addr.get}' "}""" 
      else
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid}"}"""
    
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
        address = c.obj.get("proxyAddress").map(_.str).getOrElse(c("address").str), 
        network = c("chainUid").str, 
        name = c("name").str,
        addressImpl = Some(c("address").str)
      )
    }
    contracts.toSeq
  }

  def addContract(pid:String, addr:String, network:String, name:String):Contract = {
    val url = s"${baseUrl}/contract"
    val data = 
      s"""{"address":"${addr}","chainUid":"${network}","name":"${name}","projectId":${pid},"addressType":"CONTRACT"}"""
    
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
      )
    }.toSeq

    if(did.isDefined)
      ss.filter(_.did == did.get)
    else
      ss
  }

  def addDetector(pid:String, cid:String, did:String, name:String, tags:Seq[String] = Seq("COMPLIANCE"),sev:Int = -1, conf:ujson.Obj = Map.empty):Detector = {
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
    
    val confData = conf.copy(conf.value += "severity" -> ujson.Num(sev))
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

  def delDetector(detectorId:String, name:Option[String] = None):Detector = {    
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
}
