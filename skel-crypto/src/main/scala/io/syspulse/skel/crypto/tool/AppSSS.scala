package io.syspulse.skel.crypto.tool

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.crypto.Eth
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.abi.datatypes.generated.Uint256
import java.util.Arrays
import org.web3j.abi.TypeReference
import io.syspulse.skel.crypto.SSS
import org.secret_sharing.Share

object AppSSS extends {

  case class Config(
    cmd:String = "create",
    params:Seq[String] = Seq()
  )

  implicit class ShareOps(share: Share) {
    def str: String = {
      s"${share.x}:${share.y}:${Util.hex(share.hash.toArray)}:${share.primeUsed}"
    }
  }
  
  def main(args:Array[String]) = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")
    
    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"crypto-sss","",
        
        ArgCmd("create","create shares: <secret> <m> <n>"),
        ArgCmd("recover","recover secret: <share1> <share2> <share3>"),        
        
        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    val r =config.cmd match {
      case "create" => 
        if(config.params.size < 3) {
          Console.err.println("create: <secret> <m> <n>")
          sys.exit(1)
        }

        val secret = config.params(0)
        val m = config.params(1).toInt
        val n = config.params(2).toInt        
        
        SSS.createShares(secret,m,n)
          .map(shares => shares.map(_.str).mkString("\n"))
              
      case "recover" => 
        
        def parseShare(share:String,i:Int ):Share = {
          share.split(":").toList match {
            case x :: y :: hash :: prime :: Nil => 
              Share(BigInt(x),BigInt(y),Util.fromHexString(hash).toList,prime)
            case y :: hash :: prime :: Nil => 
              Share(BigInt(i),BigInt(y),Util.fromHexString(hash).toList,prime)
            case _ => throw new IllegalArgumentException(s"Invalid share: ${share}")
          }
        }

        val shares = config.params.zipWithIndex.map{ case(share,i) => parseShare(share,i) }        
        SSS.getSecret(shares.toList).map(s => new String(s))
      
    }
    
    Console.err.println(s"r = ${r}")
  }
}

