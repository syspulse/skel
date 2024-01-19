package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

trait Runtime[T] {

  def start():Try[Any]
  def stop():Try[Any]
  def id():Try[String]
}
