package io.syspulse.skel.uri

/* 
kafka://broker:9092/topic/group/offset
kafka://broker:9092/topic/group/offset?raw - automatically convert to raw ByteString
kafka://broker:9092/topic/group/offset?json - automatically convert to json (default)
kafka://broker:9092/topic/group/offset?past=3600000&freq=30000&max=10

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

  private val (_broker:String,_topic:String,_group:String,_offset:String,_ops:Map[String,String]) = parse(uri)

  def broker:String = _broker
  def topic:String = _topic
  def group:String = _group
  def offset:String = _offset
  def isRaw:Boolean = _ops.get("raw").isDefined
  def autoCommit:Boolean = _ops.get("autocommit").map(v => v.isEmpty || v.toBoolean).getOrElse(true)
  def ops:Map[String,String] = _ops

  def parse(uri:String):(String,String,String,String,Map[String,String]) = {
    // resolve options
    val (url:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url,Map())
      case url :: ops => 
        
        val vars = ops.flatMap(_.split("=").toList match {
          case k :: Nil => Some(k -> "")
          case k :: v :: Nil => Some(k -> v)
          case _ => None
        }).toMap
        
        (url,vars)
      case _ => 
        ("",Map())
    }

    // var raw = false
    // ops.toLowerCase.split(",").toList.foreach{ _ match {
    //   case "raw" => raw = true
    //   case "json" => raw = false
    //   case _ => raw = false // default raw is false and will be converted to json
    // }}
      
    url.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: topic :: group :: offset :: _ => (host,topic,group,offset,ops)
      case host :: topic :: group :: Nil => (host,topic,group,"earliest",ops)
      case host :: topic :: Nil => (host,topic,"group","earliest",ops)
      case host :: Nil => (host,"topic","group","earliest",ops)
      case _ => ("","","","earliest",ops)
    }
  }
}