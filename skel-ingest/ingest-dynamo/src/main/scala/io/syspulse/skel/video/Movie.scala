package io.syspulse.skel.video

import scala.util.Random

case class Movie(vid:String = s"M-${Math.abs(Random.nextInt())}",ts:Long = System.currentTimeMillis,title:String)

