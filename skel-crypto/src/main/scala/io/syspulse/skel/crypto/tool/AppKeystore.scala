package io.syspulse.skel.crypto.tool

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger

import scopt.OParser
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.crypto.Eth2

case class Config(
  keystoreFile:String="",
  keystorePass:String="",
  keystoreType:String="eth1"
)

object AppKeystore extends {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  def main(args:Array[String]) = {
    println(s"args: ${args.size}: ${args.toSeq}")
    
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"tool-keystore","",
        ArgString('w', "keystore.file","Wallet File (def: keystore.json)"),
        ArgString('p', "keystore.pass","Wallet Password (def: test123)"),
        ArgString('t', "keystore.type","Wallet type (def: eth1)"),
        
      )
    ))
    
    val config = Config(
      keystoreFile = c.getString("keystore.file").getOrElse("keystore.json"),
      keystorePass = c.getString("keystore.pass").getOrElse("test123"),
      keystoreType = c.getString("keystore.type").getOrElse("eth1"),
    )

    config.keystoreType.toLowerCase match {
      case "eth1" => { 
        val (sk,pk) = Eth.generateRandom()
        val addr = Eth.address(pk)
        Console.println(s"${Util.hex(sk)},${Util.hex(pk)},${addr}")
        val f = Eth.writeKeystore(sk,pk,config.keystorePass,config.keystoreFile)
        Console.println(s"file: ${f}")
      }
      case "eth2" => {
        val bls = Eth2.generateRandom()
        val addr = Eth.address(bls.pk)
        Console.println(s"${Util.hex(bls.sk)},${Util.hex(bls.pk)},${addr}")
      }
    }
    
  }
}

