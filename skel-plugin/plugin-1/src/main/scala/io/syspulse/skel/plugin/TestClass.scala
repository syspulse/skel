package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

case class TestClass(
  data:String = "no",
  id:Int = 0,
  ts:Long = System.currentTimeMillis
) 
