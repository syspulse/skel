package io.syspulse.crypto.eth.abi

import com.typesafe.scalalogging.Logger

// ATTENTION
import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

abstract class ContractAbi(addr:String,abi:Seq[AbiDefinition]) {
  override def toString = s"${getClass().getSimpleName()}(${addr},${abi.size})"

  def getAbi():Seq[AbiDefinition] = abi
  
}

case class ERC20Abi(addr:String,abi:Seq[AbiDefinition],funcTo:String, funcValue:String, tokenName:String="") 
  extends ContractAbi(addr,abi)
{
  def name:String = tokenName
  override def toString = s"${getClass().getSimpleName()}(${addr},${abi.size},${name},${funcTo},${funcValue})"
}

abstract class AbiRepo {
  protected val log = Logger(s"${this.getClass()}")

  def load():Map[String,ContractAbi]
}

class AbiRepoFiles(dir:String) extends AbiRepo {

  def load():Map[String,ContractAbi] = {
    log.info(s"scanning ABI: ${dir}")
    val abis = os.list(os.Path(dir,os.pwd)).flatMap( f => {        
      val (name,addr) = f.last.split("[-.]").toList match {
        case name :: addr :: _ => (name,addr.toLowerCase())
        case name :: Nil => (name,"")
        case _ => ("","")
      }

      if(!addr.isEmpty()) {
        val abi = Decoder.loadAbi(scala.io.Source.fromFile(f.toString).getLines().mkString("\n"))
        if(abi.size != 0) {
          log.info(s"${f}: ${abi.size}")

          val functionName = "transfer"
          // find function names
          val function = abi.filter(d => d.isFunction).find(_.name.get == functionName)
          if(function.isDefined) {

            val parToName = function.get.inputs.get(0).name
            val parValueName = function.get.inputs.get(1).name
            Some(ERC20Abi( addr, abi, parToName, parValueName, name ))

          } else {
            log.warn(s"${f}: could not find function: '${functionName}'")
            None
          }
        } else {
          log.warn(s"${f}: failed to load: ${abi}")
          None
        }
      }
      else {
        log.warn(s"${f}: could not determine addr")
        None
      }
    })

    // map of addr -> TokenAbi
    val erc20s = abis.map(ta => ta.addr -> ta).toMap
    log.info(s"ABI: ${erc20s}")
    erc20s
  }
}

// abstract class AbiRepos[A <: AbiRepos[A]] {
//   //def decode(selector:String)
//   def withRepo(repo:AbiRepo):A
// }

// class AbiReposLoaded extends AbiRepos[AbiReposLoaded] {

// }

class ABI {
  protected val log = Logger(s"${this.getClass()}")

  var repos:List[AbiRepo] = List()
  var contracts:Map[String,ContractAbi] = Map()

  var functions:Map[String,String] = Map(
    "0xa9059cbb" -> "transfer",
    "0x23b872dd" -> "transferFrom",
  )

  def size = repos.size

  def findToken(addr:String):Option[ERC20Abi] = {
    contracts.values.collectFirst( c => c match {
      case t:ERC20Abi if t.addr.equalsIgnoreCase(addr) => t
    })
  }
  
  def withRepo(repo:AbiRepo):ABI = {
    repos = repos :+ repo
    this
  }

  def load():ABI = {
    contracts = repos.foldLeft(Map[String,ContractAbi]())( (m,r)  => m ++ r.load() )
    this
  }

  def decodeInput(contract:String,input:String,selector:String = "") = {
    val abi = contracts.get(contract.toLowerCase())

    val funcHash = input.take(ABI.FUNC_HASH_SIZE).toLowerCase()
    val func = if(selector.isEmpty) functions.get(funcHash).getOrElse("") else selector

    val intputData = input.drop(ABI.FUNC_HASH_SIZE)

    if(abi.isDefined && (!func.isEmpty)) {
      Decoder.decodeInput(abi.get.getAbi(),func,intputData)
    } else 
      Failure(new Exception(s"could not find ABI for contract: '${contract}'(${func})"))
  }
}

object ABI {
  val FUNC_HASH_SIZE = "0x12345678".size
}

object AbiRepo {
  
  def build() = new ABI()
}

