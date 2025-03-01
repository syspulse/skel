package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.util.Util

case class Cachable[V](
  v:V,
  var ts:Long,
  var checks:Int = 0
)

trait CacheThread {
  def clean():Long
  def cleanFreq:Long

  private val cleanThr = new Thread(() => {
    while (true) {
      try {
        Thread.sleep(cleanFreq)
        clean()
      } catch {
        case _: InterruptedException => 
      }
    }
  })
  cleanThr.setDaemon(true)  // Makes thread not prevent JVM shutdown
  cleanThr.start()

}