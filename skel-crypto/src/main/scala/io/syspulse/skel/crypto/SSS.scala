package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import scala.jdk.CollectionConverters

import java.security.Security

import org.secret_sharing._
import SSSS._

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.key

object SSS {

  def createShares(data:String,requiredShares:Int=3, totalShares:Int=5):Try[List[Share]] = {
    SSSS.shares(secret = data, requiredParts = requiredShares, totalParts = totalShares) match {
      case Right(shares) => Success(shares)
      case Left(err) => Failure(new Exception(err.toString))
    }
  }

  def getShares(secretShares:List[Share]):Try[String] = {
    SSSS.combine(secretShares.take(secretShares.size)) match {
      case Right(data) => Success(data)
      case Left(err) => Failure(new Exception(err.toString))
    }
  }

}


