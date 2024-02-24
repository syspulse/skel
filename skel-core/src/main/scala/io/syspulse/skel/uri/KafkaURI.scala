package io.syspulse.skel.uri

/* 
kafka://broker:9092/topic/group/offset
kafka://broker:9092/topic/group/offset?json - automatically convert to json
*/
case class KafkaURI(uri:String) {
  val PREFIX = "kafka://"

  private val (kbroker:String,ktopic:String,kgroup:String,koffset:String,kraw:Boolean) = parse(uri)

  def broker:String = kbroker
  def topic:String = ktopic
  def group:String = kgroup
  def offset:String = koffset
  def isRaw:Boolean = kraw

  def parse(uri:String):(String,String,String,String,Boolean) = {
    // resolve options
    val (url:String,ops:String) = uri.split("\\?").toList match {
      case url :: Nil => (url,"")
      case url :: ops :: Nil => (url,ops)      
      case _ => ("","")
    }

    var raw = false
    ops.toLowerCase.split(",").toList.foreach{ _ match {
      case "raw" => raw = true
      case "json" => raw = false
      case _ => raw = true // default raw is true
    }}
      
    url.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: offset :: _ => (host,topic,group,offset,raw)
      case host :: topic :: group :: Nil => (host,topic,group,"earliest",raw)
      case host :: topic :: Nil => (host,topic,"group","earliest",raw)
      case host :: Nil => (host,"topic","group","earliest",raw)
      case _ => ("","","","earliest",raw)
    }
  }
}