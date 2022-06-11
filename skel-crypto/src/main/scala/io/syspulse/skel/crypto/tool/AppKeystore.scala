package io.syspulse.skel.crypto.tool

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger

import scopt.OParser
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.crypto.Eth2
import scala.util.Success
import scala.util.Try
import io.syspulse.skel.crypto.KeyPair

case class Config(
  keystoreFile:String="",
  keystorePass:String="",
  keystoreMnemo:String="",
  keystoreType:String="eth1",
  cmd:String = ""
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
        ArgString('m', "keystore.mnemo","Mnemo or PrivateKey (starts with 0x)"),
        ArgString('t', "keystore.type","Wallet type (def: eth1)"),
        ArgCmd("read","read command"),
        ArgCmd("write","write command"),
        ArgParam("<params>","...")
      )
    ))
    
    val config = Config(
      keystoreFile = c.getString("keystore.file").getOrElse("keystore.json"),
      keystorePass = c.getString("keystore.pass").getOrElse("test123"),
      keystoreType = c.getString("keystore.type").getOrElse("eth1"),
      keystoreMnemo = c.getString("keystore.mnemo").getOrElse(""),
      cmd = c.getCmd().getOrElse("read")
    )

    println(s"config=${config}")

    config.cmd match {
      case "write" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val ecdsa:Try[KeyPair] = {
              if(config.keystoreMnemo.isBlank) 
                Eth.generateRandom() 
              else if(config.keystoreMnemo.trim.startsWith("0x")) 
                Eth.generate(config.keystoreMnemo.trim)
              else
                Eth.generateFromMnemo(config.keystoreMnemo)
            }
            if(ecdsa.isFailure) {
              Console.err.println(s"Could not generate ECDSA: ${ecdsa}")
              System.exit(1);
            }

            val kp = ecdsa.get

            val addr = Eth.address(kp.pk)
            Console.println(s"${kp},${addr}")
            val f = Eth.writeKeystore(kp.sk,kp.pk,config.keystorePass,config.keystoreFile)
            Console.println(s"eth1: ${f}")
          }

          case "eth2" => {
            val bls = {
              if(config.keystoreMnemo.isBlank) 
              Eth2.generateRandom() 
              else  if(config.keystoreMnemo.trim.startsWith("0x")) 
                Eth2.generate(Util.fromHexString(config.keystoreMnemo.trim))
              else
                Eth2.generate(config.keystoreMnemo.trim)
            }.get
          
            val addr = Eth.address(bls.pk)
            Console.println(s"${Util.hex(bls.sk)},${Util.hex(bls.pk)},${addr}")
            val f = Eth2.writeKeystore(bls.sk,bls.pk,config.keystorePass,config.keystoreFile)
            Console.println(s"eth2: ${f}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }
      case "read" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val kp = Eth.readKeystore(config.keystorePass,config.keystoreFile)
            Console.println(s"eth1: ${kp}")
          }

          case "eth2" => {
            val kp = Eth2.readKeystore(config.keystorePass,config.keystoreFile)
            Console.println(s"eth2: ${kp}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }        
    }
    
  }
}

