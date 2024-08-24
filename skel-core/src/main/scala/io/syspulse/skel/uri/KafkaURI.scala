package io.syspulse.skel.uri

/* 
kafka://broker:9092/topic/group/offset
kafka://broker:9092/topic/group/offset?raw - automatically convert to raw ByteString
kafka://broker:9092/topic/group/offset?json - automatically convert to json (default)

offsets:
   latest - from latest committed offset or from the end if no offset is found
   earliest - from earliest committed offset or from the end if no offset is found
   
   oldest - from the beginning by resetting offset to 0. Auto committing!
   youngest - from the end by resetting offset to 0. Auto committing!
   
   earliest_noauto | start - from earliest and no commit (will read from last commit)
   latest_noauto | end - from earliest and no commit (will read from last commit)
   
   START - from the beginning by resetting offset to 0. No autocommit
   END - from the end by resetting offset to 0. No autocomit
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
      case _ => raw = false // default raw is false and will be converted to json
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