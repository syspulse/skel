package io.syspulse.skel.ai.agent

import java.util.concurrent.TimeUnit
import io.syspulse.skel.util.Util
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

case class Contract(contractId:String, address:String, network:String, name:String)

class ExtClient(baseUrl:String, accessToken0:Option[String] = None) {
  protected val log = Logger(getClass)
  
  val accessToken = accessToken0
    .orElse(sys.env.get("ACCESS_TOKEN"))
    .orElse(Option(os.read(os.Path("ACCESS_TOKEN",os.pwd))))
    .orElse(sys.env.get("EXT_PILOT_TOKEN"))
    .getOrElse(throw new RuntimeException("missing accessToken"))

  def getProjectContracts(pid:String, addr:Option[String] = None, name:Option[String] = None):Seq[Contract] = {
    val url = s"${baseUrl}/contract/search"
    val data = 
      if(addr.isDefined) 
        s"""{"from":0,"size":10,"trackTotal":false,"where":"projectId = ${pid} AND address='${addr.get}' "}""" 
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
        c("address").str, 
        c("chainUid").str, 
        c("name").str
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
}
