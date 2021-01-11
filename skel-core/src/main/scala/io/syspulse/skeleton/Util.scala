package io.syspulse.skeleton

import java.time._
import java.time.format._
import java.time.temporal._
import java.util.Locale


object Util {

  val formatLong = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSS")

  def now:String = formatLong.format(LocalDateTime.now)

}


