package io.syspulse.skel.notify.telegram

/* 
tel://channel/key
*/
case class TelegramURI(uri:String,configUri:Option[String]=None) {
  val PREFIX = "tel://"

  val (tchannel:String,tapikey:String) = parse(uri)

  def channel:String = tchannel
  def key:String = tapikey
  
  def parse(uri:String):(String,String) = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case channel :: key :: _ => (channel,key)
      case "" :: Nil => parse(configUri.getOrElse(""))
      case channel :: Nil => (channel,configUri.getOrElse(""))      
      case _ => ("","")
    }
  }

  override def toString() = s"tel://${channel}/${key}"
}