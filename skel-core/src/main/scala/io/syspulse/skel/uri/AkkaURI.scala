package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/*
  
*/
case class AkkaURI(uri0:String) {
  
  private val (_system:String,_actor:String,_host:Option[String],_port:Option[Int]) = parse(uri0)

  def system:String = _system
  def actor:String = _actor  
  def host = _host
  def port = _port
  def timeout:Long = 5000

  def parse(uri: String) = {
    uri match {
      case s"akka://$systemName@$systeHost:$systemPort/$path" =>
        (systemName, path, Some(systeHost), Some(systemPort.toInt))
      case s"akka://$systemName/$path" =>
        (systemName, path, None, None)
      case _ =>
        throw new IllegalArgumentException(s"Invalid Akka URI format: $uri")
    }
  } 
  
}