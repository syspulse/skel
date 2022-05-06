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
  keystoreMnemo:String="",
  keystoreType:String="eth1",
  cmd:Seq[String] = Seq()
)

object AppKeystore extends {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  import io.syspulse.skel.crypto.key._

  def main(args:Array[String]) = {
    println(s"args: ${args.size}: ${args.toSeq}")
    
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"tool-keystore","",
        ArgString('w', "keystore.file","Wallet File (def: keystore.json)"),
        ArgString('p', "keystore.pass","Wallet Password (def: test123)"),
        ArgString('p', "keystore.mnemo","Mnemonic (for creating keystore)"),
        ArgString('t', "keystore.type","Wallet type (def: eth1)"),
        ArgParam("<cmd>","commands ('write','read') (def: write)")
      )
    ))
    
    val config = Config(
      keystoreFile = c.getString("keystore.file").getOrElse("keystore.json"),
      keystorePass = c.getString("keystore.pass").getOrElse("test123"),
      keystoreType = c.getString("keystore.type").getOrElse("eth1"),
      keystoreMnemo = c.getString("keystore.mnemo").getOrElse(""),
      cmd = c.getParams()
    )

    config.cmd.headOption.getOrElse("write").toLowerCase() match {
      case "write" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val (sk,pk) = if(config.keystoreMnemo.isBlank) Eth.generateRandom() else {
              val k = Eth.generateFromMnemo(config.keystoreMnemo)
              if(k.isFailure) {
                Console.err.println(s"Could not generate BLS: ${k}")
                System.exit(1);
              }
              k.get
            }
            val addr = Eth.address(pk)
            Console.println(s"${Util.hex(sk)},${Util.hex(pk)},${addr}")
            val f = Eth.writeKeystore(sk,pk,config.keystorePass,config.keystoreFile)
            Console.println(s"result: ${f}")
          }

          case "eth2" => {
            val bls = Eth2.generateRandom()
            val addr = Eth.address(bls.pk)
            Console.println(s"${Util.hex(bls.sk)},${Util.hex(bls.pk)},${addr}")
            val f = Eth2.writeKeystore(bls.sk,bls.pk,config.keystorePass,config.keystoreFile)
            Console.println(s"result: ${f}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }
      case "read" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val k = Eth.readKeystore(config.keystorePass,config.keystoreFile)
            Console.println(s"result: ${k}")
          }

          case "eth2" => {
            val k= Eth2.readKeystore(config.keystorePass,config.keystoreFile)
            Console.println(s"result: ${k}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }        
    }
    
  }
}

