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

object AppKeystore extends {
  case class Config(
    keystoreFile:String="",
    keystorePass:String="",
    keystoreSK:String="",
    keystoreType:String="eth1",
    cmd:String = "",
    params:Seq[String] = Seq()
  )
  
  import io.syspulse.skel.crypto.key._

  def main(args:Array[String]) = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")

    val d = Config() 
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-keystore","",
        ArgString('w', "keystore.file","Wallet File (def: keystore.json)"),
        ArgString('p', "keystore.pass","Wallet Password (def: test123)"),
        ArgString('k', "keystore.key","Mnemo phase or PrivateKey (starts with 0x)"),
        ArgString('t', "keystore.type","Wallet type (def: eth1)"),
   
        ArgCmd("read","read command"),
        ArgCmd("write","write command"),
        ArgCmd("sign","sign command"),
        ArgCmd("recover","recover command"),
        ArgCmd("mnemonic","Mnemonic command"),
        ArgCmd("generate","Generate from SK"),
   
        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      keystoreFile = c.getString("keystore.file").getOrElse("keystore.json"),
      keystorePass = c.getString("keystore.pass").getOrElse("test123"),
      keystoreType = c.getString("keystore.type").getOrElse("eth1"),
      keystoreSK = c.getString("keystore.key").getOrElse(""),
      cmd = c.getCmd().getOrElse("read"),
      params = c.getParams()

    )

    Console.err.println(s"config=${config}")

    config.cmd match {
      case "write" => 
        config.keystoreType.toLowerCase match {
          
          case "eth1" => { 
            val ecdsa:Try[KeyPair] = {
              if(config.keystoreSK.isBlank) 
                Eth.generateRandom() 
              else if(config.keystoreSK.trim.startsWith("0x")) 
                Eth.generate(config.keystoreSK.trim)
              else
                Eth.generateFromMnemo(config.keystoreSK)
            }
            if(ecdsa.isFailure) {
              Console.err.println(s"Could not generate ECDSA: ${ecdsa}")
              System.exit(1);
            }

            val kp = ecdsa.get

            val addr = Eth.address(kp.pk)
            println(s"${kp})")
            println(s"addr=${addr}")
            val f = Eth.writeKeystore(kp.sk,kp.pk,config.keystorePass,config.keystoreFile)
            println(s"eth1: ${f}")
          }

          case "eth2" => {
            val bls = {
              if(config.keystoreSK.isBlank) 
              Eth2.generateRandom() 
              else  if(config.keystoreSK.trim.startsWith("0x")) 
                Eth2.generate(Util.fromHexString(config.keystoreSK.trim))
              else
                Eth2.generate(config.keystoreSK.trim)
            }.get
          
            val addr = Eth.address(bls.pk)
            println(s"${Util.hex(bls.sk)},${Util.hex(bls.pk)},${addr}")
            val f = Eth2.writeKeystore(bls.sk,bls.pk,config.keystorePass,config.keystoreFile)
            println(s"eth2: ${f}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }
      case "read" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val kp = Eth.readKeystore(config.keystorePass,config.keystoreFile)
            println(s"eth1: ${kp}")
          }

          case "eth2" => {
            val kp = Eth2.readKeystore(config.keystorePass,config.keystoreFile)
            println(s"eth2: ${kp}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }
  
      case "sign" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val kp = Eth.readKeystore(config.keystorePass,config.keystoreFile)
            if(kp.isFailure) {
              Console.println(s"eth1: sign: ${kp}")
              System.exit(1)              
            }
            val msg = config.params.mkString(" ")
            val sig = Eth.signMetamask(msg,kp.get)
            println(s"eth1: sk = ${Util.hex(kp.get.sk)}\n sign(${msg})\n sig = ${sig}\n ${Util.hex(sig.toArray())}")
          }

          case "eth2" => {
            val kp = Eth2.readKeystore(config.keystorePass,config.keystoreFile)
            if(kp.isFailure) {
              println(s"eth1: sign: ${kp}")
              System.exit(1)              
            }
            
            val msg = config.params.mkString(" ")            
            val sig = Eth2.sign(kp.get.sk,msg)

            println(s"eth2: sk = ${Util.hex(kp.get.sk)}\n sign(${msg})\n sig = ${Util.hex(sig)}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }
      case "recover" => 
        config.keystoreType.toLowerCase match {
          case "eth1" => { 
            val kp = Eth.readKeystore(config.keystorePass,config.keystoreFile)
            if(kp.isFailure) {
              Console.err.println(s"eth1: recover: ${kp}")
              System.exit(1)              
            }

            val sig = config.params.head
            val msg = config.params.tail.mkString(" ")
            
            val pk = Eth.recoverMetamask(msg,Util.fromHexString(sig))
            if(pk.isFailure) {
              Console.err.println(s"eth1: recover(${msg},${sig}}): ${pk}")
              System.exit(1)
            }
            println(s"eth1: recover(${msg},${sig}})\n pk = ${Util.hex(pk.get)}\n addr = ${Eth.address(pk.get)}")
          }

          case "eth2" => {
            val kp = Eth2.readKeystore(config.keystorePass,config.keystoreFile)
            if(kp.isFailure) {
              Console.err.println(s"eth1: sign: ${kp}")
              System.exit(1)              
            }
            
            val sig = config.params.head
            val msg = config.params.tail.mkString(" ")
            //val pk = Eth2.recover(msg,Util.fromHexString(sig))

            println(s"eth2: sk=${Util.hex(kp.get.sk)}: sign(${msg}): ${sig}")
          }

          case _ => {
            Console.err.println(s"Unknown type: ${config.keystoreType}")
          }
        }

      case "mnemonic" => 

        val kp = config.params.toList match {
          case words12 :: Nil => 
            val mnemo = words12
            val path = "m/44'/60'/0'/0"
            Console.err.println(s"Mnemonic: path=${path}: '${mnemo}'")
            Eth.generateFromMnemoPath(mnemo,path)

          case "metmask" :: word1 :: words11  => 
            val mnemo = word1 + " " + words11.mkString(" ")
            Console.err.println(s"Mnemonic: '${mnemo}'")
            Eth.generateFromMnemoMetamask(mnemo)

          case "metmask" :: words12  => 
            val mnemo = words12.mkString(" ")
            Console.err.println(s"Mnemonic: '${mnemo}'")
            Eth.generateFromMnemoMetamask(mnemo)

          case "path" :: "ethereum" :: words12 => 
            val mnemo = words12.mkString(" ")
            val path = "m/44'/60'/0'/0"
            Console.err.println(s"Mnemonic: path=${path}: '${mnemo}'")
            Eth.generateFromMnemoPath(mnemo,path)
          
          case "path" :: "vechain" :: words12 => 
            val mnemo = words12.mkString(" ")
            val path = "m/44'/818'/0'/0"
            Console.err.println(s"Mnemonic: path=${path}: '${mnemo}'")
            Eth.generateFromMnemoPath(mnemo,path)
          
          case "path" :: path :: words12 => 
            val mnemo = words12.mkString(" ")
            Console.err.println(s"Mnemonic: path=${path}: '${mnemo}'")
            Eth.generateFromMnemoPath(mnemo,path)

          case word1 :: words11 => 
            val mnemo = word1 + " " + words11.mkString(" ")
            val path = "m/44'/60'/0'/0"
            Console.err.println(s"Mnemonic: path=${path}: '${mnemo}'")
            Eth.generateFromMnemoPath(mnemo,path)

          
          case Nil => 
            Console.err.println(s"Invalid mnemonic")
            sys.exit(1)
        }

        if(kp.isFailure) {
          Console.err.println(s"eth1: mnemoic: ${kp}")
          sys.exit(1)              
        }
        println(s"sk=${Util.hex(kp.get.sk)}: addr=${Eth.address(kp.get.pk)}")

      case "generate" => 
          val kp = config.params.toList match {
            case sk :: Nil => 
              Console.err.println(s"SK: '${sk}'")
              Eth.generate(sk)
            case "eth1" :: sk :: Nil => 
              Console.err.println(s"SK: '${sk}'")
              Eth.generate(sk)
            case _ => 
              Console.err.println(s"Invalid SK")
              sys.exit(1)
          }
          println(s"sk=${Util.hex(kp.get.sk)}: addr=${Eth.address(kp.get.pk)}")
    }
    
  }
}

