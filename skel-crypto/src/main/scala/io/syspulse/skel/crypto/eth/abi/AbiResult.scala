package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try

case class AbiResult(name:String,params:Seq[(String,String,Any)])
