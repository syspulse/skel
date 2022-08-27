package io.syspulse.skel.ingest.uri

/* 
kafka://host:port/topic/group
*/
case class KafkaURI(uri:String) {
  val PREFIX = "kafka://"

  val (kbroker:String,ktopic:String,kgroup:String) = (getUrl(),getTopic(),getGroup())

  def broker:String = kbroker
  def topic:String = ktopic
  def group:String = kgroup

  def getUrl():String = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: _ => host
      case host :: topic :: Nil => host
      case host :: Nil => host
      case _ => ""
    }
  }

  def getTopic():String = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: _ => topic
      case host :: topic :: Nil => topic
      case host :: Nil => ""
      case _ => ""
    }
  }

  def getGroup():String = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: _ => group
      case host :: topic :: Nil => ""
      case host :: Nil => ""
      case _ => ""
    }
  }
}