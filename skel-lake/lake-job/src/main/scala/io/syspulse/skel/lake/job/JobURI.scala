package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

import io.syspulse.skel.lake.job.livy.LivyHttp

object JobUri {

  def apply(uri:String,timeout:Long=5000L):JobEngine = {
    uri.split("://").toList match {
    
      case "livy" :: loc :: u => new LivyHttp(loc + "://" + u.mkString(""))(timeout)
      
      case _ => new JobStdout
    }    
  }
}

