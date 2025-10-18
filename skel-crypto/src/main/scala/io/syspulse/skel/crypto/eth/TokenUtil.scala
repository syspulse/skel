package io.syspulse.skel.crypto.eth

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import java.math.RoundingMode
import java.util.Locale
import java.text.DecimalFormat

import io.syspulse.skel.util.Util

import io.syspulse.skel.blockchain.Token
import io.syspulse.skel.crypto.eth.Web3jTrace
import io.syspulse.skel.crypto.Eth

object TokenUtil {

  def isBurnAddr(addr:String,regexp:Option[String] = None) = {
    if(regexp.isDefined) {
      try {
        addr.matches(regexp.get)
      } catch {
        case e:Exception => false
      }
    } else
      addr.startsWith("0x000000000000000000000000")
  }

  def isMintAddr(addr:String,regexp:Option[String] = None) = isBurnAddr(addr,regexp)
      
  //val NATIVE_ID = "native"
  //val tokenFormat = new DecimalFormat("#,##0.000");
  val tokenFormat = new DecimalFormat("#,##0.00")
  tokenFormat.setRoundingMode(RoundingMode.DOWN)
  val tokenFormatUS = new DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US))
  tokenFormatUS.setRoundingMode(RoundingMode.DOWN)
  
  val tokenFormat3 = new DecimalFormat("#,##0.0000")
  tokenFormat3.setRoundingMode(RoundingMode.DOWN)
  val tokenFormat3US = new DecimalFormat("#,##0.0000", new java.text.DecimalFormatSymbols(Locale.US))
  tokenFormat3US.setRoundingMode(RoundingMode.DOWN)

  val THOUSAND = 1000.0
  val MILLION = THOUSAND * 1000.0
  val BILLION = MILLION * 1000.0
  val TRILLION = BILLION * 1000.0
  val QUADRILLION = TRILLION * 1000.0
  val QUINTILLION = QUADRILLION * 1000.0
  val SEXTILLION = QUINTILLION * 1000.0
  val SEPTILLION = SEXTILLION * 1000.0
  val OCTILLION = SEPTILLION * 1000.0
  val NONILLION = OCTILLION * 1000.0
  val DECILLION = NONILLION * 1000.0
  val UNDECILLION = DECILLION * 1000.0
  val DUODECILLION = UNDECILLION * 1000.0
  val TREDECILLION = DUODECILLION * 1000.0
  val QUATTUORDECILLION = TREDECILLION * 1000.0

  def toHumanWithThresh(v0:BigDecimal,dec:Option[Int] = None,thresh:Option[Double] = None):String = {
    if(!thresh.isDefined) return TokenUtil.tokenFormatUS.format(v0)
    
    val v = if(dec.isDefined) v0 / BigDecimal(10).pow(dec.get) else v0

    val (v1,suffix,us) = 
    if(thresh.get >= QUATTUORDECILLION && v.abs >= QUATTUORDECILLION) {
      (v / QUATTUORDECILLION, "QUATTUORDECILLION",true)
    } else 
    if(thresh.get >= TREDECILLION && v.abs >= TREDECILLION) {
      (v / TREDECILLION, "TREDECILLION",true)
    } else 
    if(thresh.get >= DUODECILLION && v.abs >= DUODECILLION) {
      (v / DUODECILLION, "DUODECILLION",true)
    } else 
    if(thresh.get >= UNDECILLION && v.abs >= UNDECILLION) {
      (v / UNDECILLION, "UNDECILLION",true)
    } else 
    if(thresh.get >= NONILLION && v.abs >= NONILLION) {
      (v / NONILLION, "NONILLION",true)
    } else 
    if(thresh.get >= OCTILLION && v.abs >= OCTILLION) {
      (v / OCTILLION, "O",true)
    } else 
    if(thresh.get >= SEPTILLION && v.abs >= SEPTILLION) {
      (v / SEPTILLION, "S",true)
    } else 
    if(thresh.get >= SEXTILLION && v.abs >= SEXTILLION) {
      (v / SEXTILLION, "X",true)
    } else 
    if(thresh.get >= QUINTILLION && v.abs >= QUINTILLION) {
      (v / QUINTILLION, "QT",true)
    } else 
    if(thresh.get >= QUADRILLION && v.abs >= QUADRILLION) {
      (v / QUADRILLION, "Q",true)
    } else 
    if(thresh.get >= TRILLION && v.abs >= TRILLION) {
      (v / TRILLION, "T",true)
    } else 
    if(thresh.get >= BILLION && v.abs >= BILLION) {
      (v / BigDecimal(thresh.get), "B",true)
    } else if(thresh.get >= MILLION && v.abs >= MILLION) {
      (v / MILLION, "M",true)
    } else if(thresh.get >= THOUSAND && v.abs >= THOUSAND) {
      (v / THOUSAND, "K",true)
    } else {
      (v, "", if(dec.isDefined) true else true)
    }
    if(us) 
      s"${TokenUtil.tokenFormatUS.format(v1)}${suffix}"
    else 
      s"${TokenUtil.tokenFormat.format(v1)}${suffix}"
  }

  def toHuman(v:Double):String = toHumanWithThresh(BigDecimal(v),None,Some(TREDECILLION))
  def toHuman(v:BigInt):String = toHumanWithThresh(BigDecimal(v),None,Some(TREDECILLION))
  def toHuman(v:BigInt,dec:Int) = toHumanWithThresh(BigDecimal(v),Some(dec),Some(TREDECILLION))
  

  def askErc20(addr:String,chain:String)(web3:Web3jTrace):Try[Token] = {    
    val t = for {
       r <- Eth.callFunction(addr, addr, "decimals()(uint)", Seq.empty)(web3)    
       dec <- Success(r.toInt)
       r <- Eth.callFunction(addr, addr, "name()(string)", Seq.empty)(web3)    
       sym <- Success(r)
       r <- Eth.callFunction(addr, addr, "totalSupply()(uint256)", Seq.empty)(web3)    
       totalSupply <- Success(BigInt(r))
       t <- {
         Success(Token(
          addr = addr,
          sym = sym,
          dec = dec,
          bid = chain,
          supply = Some(totalSupply)
         ))
       }
    } yield t

    t
  }
}