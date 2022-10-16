package io.syspulse.skel.ingest.uri

/* 
smtp://smtp.gmail.com:587/user@pass
*/
case class KafkaURI(uri:String) {
  val PREFIX = "kafka://"

  val (kbroker:String,ktopic:String,kgroup:String,koffset:String) = parse(uri)

  def broker:String = kbroker
  def topic:String = ktopic
  def group:String = kgroup
  def offset:String = koffset

  def parse(uri:String):(String,String,String,String) = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: offset :: _ => (host,topic,group,offset)
      case host :: topic :: group :: Nil => (host,topic,group,"earliest")
      case host :: topic :: Nil => (host,topic,"group","earliest")
      case host :: Nil => (host,"topic","group","earliest")
      case _ => ("","","","earliest")
    }
  }
}