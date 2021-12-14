package io.syspulse.skel.crypto.wallet

import scala.util.{Try,Failure,Success}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import os._
import upickle._

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Eth
import ujson.Str



object vault {
  type SignerID = UUID
  type UserID = UUID
  type Address = String
} 

import vault._
import io.syspulse.skel.crypto.key._

case class Signer(uid:UUID,sk:SK,pk:PK) {
  val addr:Address = Eth.address(pk)
  override def toString = s"Signer(${uid},${sk},${pk},${addr})"
}

trait WalletVaultable {
  import vault._
  val log = Logger(s"${this}")

  val UNKNOWN_USER = UUID("00000000-0000-0000-0000-000000000000")

  var signers: Map[SignerID,List[Signer]] = Map()

  def msign(userSk:SK,data:Array[Byte],userId:Option[UserID]=None):List[Signature] = {
    //log.debug(s"signing: ${userId}: userSig=${userSig}: ${data}")
    val userSig = sign(userSk,data,userId)
    if(!userSig.isDefined) return List()

    val ss = signers.getOrElse(userId.getOrElse(UNKNOWN_USER),List()).map(signer => {
      val s = Eth.sign(data,signer.sk)
      //log.debug(s"signing: ${userId}: userSig=${userSig}: ${data}: ${s}")
      s
    })

    log.debug(s"signing: ${userId}: userSig=${userSig}: ${data}: ${ss}")
    userSig.get +: ss 
  }

  def sign(userSk:SK,data:Array[Byte],userId:Option[UserID]=None):Option[String] = {
    //log.debug(s"signing: ${userId}: health=${h}: data=${data}")
    val userSig = Eth.sign(data,userSk)
    //log.debug(s"signing: ${userId}: data=${data}: userSig=${userSig}")
    Some(userSig)
  }

  def verify(userSig:Signature,userPk:PK,data:Array[Byte],userId:Option[UserID]=None):Boolean = {
    Eth.verify(data,userSig,userPk)
  }

  def mverify(sig:List[Signature],userPk:PK,data:Array[Byte],userId:Option[UserID]=None):Boolean = {

    val ss:List[PK] = signers.getOrElse(userId.getOrElse(UNKNOWN_USER),List()).map(_.pk)
    val pks = userPk +: ss
    sig.zip(pks).filter( sp => Eth.verify(data,sp._1,sp._2)).size != 0
  }

  def load():Try[Map[SignerID,List[Signer]]]
}

class WalletVaultTest extends WalletVaultable {
  
  val signer1 = Signer(UUID("00000000-0000-0000-9999-00000000ff01"),"0x000000000000000000000000000000000000000000000000000000000000ff01","0x63d4523937d9f0960d3ad56140fc484cc4923f2ab6438b9320a96bc437a5fc1c62461c8143417fb81289c9a96cf3fd9b8f695eebbca80a7ab26c717441c05609")
  val signer2 = Signer(UUID("00000000-0000-0000-9999-00000000ff02"),"0x000000000000000000000000000000000000000000000000000000000000ff02","0xb7a36287c48ba57cbf33ed6bf630dc84d1196bf00f86b12266587fb55219a68fb85832290299275c26a78aab86a777fa61c589dadcc8b33e4e15d5357f2fc23f")
  val signer3 = Signer(UUID("00000000-0000-0000-9999-00000000ff03"),"0x000000000000000000000000000000000000000000000000000000000000ff03","0xd8fb72d474f217f38f86369228f3199c3f2ef7db099ff490a58fb79d26c09d2757c564a0def15f95e59206151545ee65bfd30cd679c4d5cbd602ec9226a25a95")
  
  signers = Map(
    signer1.uid -> List(signer1),
    signer2.uid -> List(signer2),
    signer3.uid -> List(signer3),
  )

  override def load():Try[Map[SignerID,List[Signer]]] = Success(signers)
}

class WalletVaultKeyfiles(keystoreDir:String = ".", passwordQuestion: (String) => String) extends WalletVaultable {
  
  override def load():Try[Map[SignerID,List[Signer]]] = {
    val dir = os.Path(keystoreDir, os.pwd)
    val ss = os.list(dir).filter(_.ext == "json").flatMap{ fileName =>

      val pass = passwordQuestion(fileName.last.toString)
      
      // keystore file must contain UUID next to address 
      // if not filename is expected to be UUID like
      val json = ujson.read(scala.io.Source.fromFile(fileName.toString).getLines().mkString)
      val uid = json.obj.getOrElse("id","") match {
        case ""   => UNKNOWN_USER
        case Str(str)  => UUID(str)
      }

      val kk = Eth.readKeystore(pass,fileName.toString)
      val ss = kk match {
        case Success(s) => log.info(s"${fileName}: ${uid}: ${kk}"); Success(uid -> List(Signer(uid,s._1,s._2))) 
        case Failure(e) => log.warn(s"${fileName}: ${uid}: ${kk}"); Failure(e)
      }
      ss.toOption
    }.toMap

    signers = ss  
    Success(signers)
  }

}

trait Buildable {
  def withWallet(w:WalletVaultable):Buildable
  def load():Long
}

object WalletVault extends Buildable {
  val log = Logger(s"${this}")
  import vault._
  var vaults = Seq[WalletVaultable]()
  var signers = Seq[Signer]()

  def withWallet(w:WalletVaultable):Buildable = {
    vaults = vaults :+ w
    this
  }

  def load():Long = {
    log.info(s"Loading vaults: ${vaults}")
    signers = vaults.flatMap( v => v.load().toOption).map(_.values).flatten.flatten
    size()
  }

  def size() = signers.size

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