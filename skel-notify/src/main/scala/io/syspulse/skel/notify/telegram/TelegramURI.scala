package io.syspulse.skel.notify.telegram

/* 
tel://channel/key
*/
case class TelegramURI(uri:String) {
  val PREFIX = "tel://"

  val (tchannel:String,tapikey:String) = parse(uri)

  def channel:String = tchannel
  def key:String = tapikey
  
  def parse(uri:String):(String,String) = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case channel :: key :: _ => (channel,key)
      case _ => ("","")      
    }
  }

  override def toString() = s"tel://${channel}/${key}"
}