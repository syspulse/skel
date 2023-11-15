package io.syspulse.skel.crypto.wallet

import scala.util.{Try,Failure,Success}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import os._
import upickle._
import ujson.Str

import org.web3j.utils.{Numeric}

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Eth


object vault {
  type SignerID = UUID
  type UserID = UUID
  type Address = String
} 

import vault._
import io.syspulse.skel.crypto.key._

case class Signer(uid:UUID,sk:SK,pk:PK) {
  val addr:Address = Eth.address(pk)
  override def toString = s"Signer(${uid},${Util.hex(sk)},${Util.hex(pk)},${addr})"
}

object Signer {
  def apply(uuid:UUID,sk:String,pk:String):Signer = Signer(uuid,Numeric.hexStringToByteArray(sk),Numeric.hexStringToByteArray(pk))
}

trait WalletVaultable {
  import vault._
  val log = Logger(s"${this}")

  val UNKNOWN_USER = UUID("00000000-0000-0000-0000-000000000000")

  var signers: Map[SignerID,Signer] = Map()

  def msign(data:Array[Byte],userSk:Option[SK]=None,userId:Option[UserID]=None):List[Signature] = {
    val signers = if(userSk.isDefined) 
                    Signer(userId.getOrElse(UNKNOWN_USER),userSk.get,Array[Byte]()) +: this.signers.values.toList
                  else 
                    this.signers.values.toList
    
    val sigs = signers.map(signer => {
      val s = Eth.sign(data,signer.sk)
      //log.debug(s"signing: uid=${userId}: signer=${signer}: data=${data}: sig=${s}")
      s
    })

    log.debug(s"signed: uid=${userId}: ${data}: sigs=${sigs}")
    sigs
  }

  def mverify(sigs:List[Signature],data:Array[Byte],userPk:Seq[PK]):Int = mverifyAddress(sigs,data,userPk.map(Eth.address(_)))

  def mverifyAddress(sigs:List[Signature],data:Array[Byte],userAddr:Seq[String]):Int = {
    
    val vv = sigs.flatMap{ sig => {
      //log.debug(s"signer=${sp}: data=${data}")
      userAddr.map( addr => Eth.verifyAddress(data, sig, addr))
    }}
    vv.filter(_ == true).size
  }

  def load():Try[Map[SignerID,Signer]]
}

class WalletVaultTest extends WalletVaultable {
  
  val signer1 = Signer(UUID("00000000-0000-0000-9999-00000000ff01"),"0x000000000000000000000000000000000000000000000000000000000000ff01","0x63d4523937d9f0960d3ad56140fc484cc4923f2ab6438b9320a96bc437a5fc1c62461c8143417fb81289c9a96cf3fd9b8f695eebbca80a7ab26c717441c05609")
  val signer2 = Signer(UUID("00000000-0000-0000-9999-00000000ff02"),"0x000000000000000000000000000000000000000000000000000000000000ff02","0xb7a36287c48ba57cbf33ed6bf630dc84d1196bf00f86b12266587fb55219a68fb85832290299275c26a78aab86a777fa61c589dadcc8b33e4e15d5357f2fc23f")
  val signer3 = Signer(UUID("00000000-0000-0000-9999-00000000ff03"),"0x000000000000000000000000000000000000000000000000000000000000ff03","0xd8fb72d474f217f38f86369228f3199c3f2ef7db099ff490a58fb79d26c09d2757c564a0def15f95e59206151545ee65bfd30cd679c4d5cbd602ec9226a25a95")
  
  signers = Map(
    signer1.uid -> signer1,
    signer2.uid -> signer2,
    signer3.uid -> signer3,
  )

  def shuffle():WalletVaultable = {
    signers = signers.map{ case(uid,ss) => {
      val uid = UUID.random
      uid -> ss.copy(uid = uid)
    }}.toMap
    this
  }
  override def load():Try[Map[SignerID,Signer]] = Success(signers)
}

trait VaultKeyfiles extends WalletVaultable {
  def getKeyStoreDir():String
  def getPasswordQuestion():(String) => Option[String]
  def getExtFilter():String

  override def load():Try[Map[SignerID,Signer]] = {

    val keystoreDir = getKeyStoreDir()
    val passwordQuestion = getPasswordQuestion() 

    val dir = os.Path(keystoreDir, os.pwd)
    val ss = os.list(dir).filter(_.ext == getExtFilter()).flatMap{ fileName =>

      val pass = passwordQuestion(fileName.last.toString)
      
      if(pass.isDefined) {
        // keystore file must contain UUID next to address 
        // if not filename is expected to be UUID like
        val json = ujson.read(scala.io.Source.fromFile(fileName.toString).getLines().mkString)
        val uid = json.obj.getOrElse("id","") match {
          case ""   => UNKNOWN_USER
          case Str(str)  => UUID(str)
        }

        val kk = Eth.readKeystore(pass.get,fileName.toString)
        val ss = kk match {
          case Success(s) => log.info(s"${fileName}: ${uid}: ${Eth.address(s.pk)}"); Success(uid -> Signer(uid,s.sk,s.pk))
          case Failure(e) => log.warn(s"${fileName}: ${uid}: ${kk}"); Failure(e)
        }
        ss.toOption
      } else 
        None

    }.toMap

    signers = ss  
    Success(signers)
  }
}

class WalletVaultKeyfiles(keystoreDir:String = ".", passwordQuestion: (String) => Option[String]) extends VaultKeyfiles {
  def getKeyStoreDir():String = keystoreDir
  def getPasswordQuestion():(String) => Option[String] = passwordQuestion
  def getExtFilter():String = "json"
}

class WalletVaultKeyfile(keystoreLocation:String, keystorePass:String) extends VaultKeyfiles {
  val fullPath = Path(keystoreLocation,os.pwd)
  val isDir = os.stat(fullPath).isDir
  def getKeyStoreDir():String = {  
    if(isDir) {
      keystoreLocation
    } else 
      fullPath.wrapped.getParent().toString
  }

  def pass(fileName:String):Option[String] = {
    if(isDir || fileName == fullPath.wrapped.getFileName().toString) Some(keystorePass)
    else None
  }

  def getPasswordQuestion():(String) => Option[String] = pass

  def getExtFilter():String = if(isDir) "json" else fullPath.ext

  //   if(os.exists(Path(keystoreFile,os.pwd))) {
  //     // keystore file must contain UUID next to address 
  //     // if not filename is expected to be UUID like
  //     val json = ujson.read(scala.io.Source.fromFile(keystoreFile).getLines().mkString)
  //     val uid = json.obj.getOrElse("id","") match {
  //       case ""   => UNKNOWN_USER
  //       case Str(str)  => UUID(str)
  //     }

  //     val kk = Eth.readKeystore(keystorePass,keystoreFile)
  //     kk match {
  //       case Success(k) => log.info(s"${keystoreFile}: ${uid}: ${Eth.address(k.pk)}"); Success(Map(uid -> List(Signer(uid,k._1,k._2)))) 
  //       case Failure(e) => log.error(s"${keystoreFile}: ${uid}: ${kk}"); Failure(e)
  //     }
      
  //   } else
  //     Failure(new Exception(s"keystore file not found: ${keystoreFile}"))
  // }

}


class WalletVault {
  val log = Logger(s"${this}")
  import vault._
  var vaults = Seq[WalletVaultable]()
  var signers = Seq[Signer]()

  def withWallet(w:WalletVaultable):WalletVault = {
    vaults = vaults :+ w
    this
  }

  def load():WalletVault = {
    log.info(s"Loading vaults: ${vaults}")
    signers = vaults.flatMap( v => v.load().toOption).map(_.values).flatten
    this
  }

  def size() = signers.size

  def msign(data:Array[Byte],userSk:Option[SK]=None,userId:Option[UserID]=None, m:Int = -1 ):List[Signature] = {
    vaults.map(_.msign(data,userSk,userId)).flatten.take(if(m == -1) signers.size else m).toList
  }

  def mverify(sigs:List[Signature],data:Array[Byte],userPk:Seq[PK],m:Int = -1):Boolean = {
    vaults.map(_.mverify(sigs,data,userPk)).sum >= (if(m == -1) signers.size else m)
  }

  def mverifyAddress(sigs:List[Signature],data:Array[Byte],userAddr:Seq[String],m:Int = -1):Boolean = {
    vaults.map(_.mverifyAddress(sigs,data,userAddr)).sum >= (if(m == -1) signers.size else m)
  }


  var timeTolernace = 1000L * 15  // 15 seconds tolerance for signature replay attack
  def now() = System.currentTimeMillis() / timeTolernace
  
  def encodeSignData(signer:Option[Signer],data:Seq[Any]=Seq()):(String,String) = {
    val d = if(data==null) now().toString else (data :+ now()).foldLeft("")(_+","+_)
    if(signer.isDefined) (Eth.sign(d,signer.get.sk),d) else ("",d)
  }

  def encodeSignDataDoubleTolerance(signer:Option[Signer],data:Seq[Any]=Seq()):Vector[(String,String)] = {
    val dd = if(data==null) 
      Vector(now().toString,(now()-1L).toString) 
    else 
      Vector(
        (data :+ now()).foldLeft("")(_+","+_),
        (data :+ now()-1L).foldLeft("")(_+","+_),
      )

    if(signer.isDefined) 
      Vector(
        (Eth.sign(dd(0),signer.get.sk),dd(0)),
        (Eth.sign(dd(1),signer.get.sk),dd(1))
      )
    else 
      Vector(
        ("",dd(0)),
        ("",dd(1))
      )
  }
}

object WalletVault {
  def build:WalletVault = new WalletVault()
}