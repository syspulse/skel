package io.syspulse.skel.notify.email

/* 
smtp://smtp.gmail.com:587/user@pass
*/
case class SmtpURI(uri:String) {
  val PREFIX = "smtp://"

  val (shost:String,sport:Int,suser:String,spass:String,stls:Option[String]) = parse(uri)

  def host:String = shost
  def port:Int = sport
  def user:String = suser
  def pass:String = spass
  def tls:Boolean = if(! stls.isDefined ) port == 465 else stls.get.toUpperCase == "TLS"
  def starttls:Boolean = if(! stls.isDefined ) port == 587 else stls.get.toUpperCase == "STARTTLS"

  def parse(uri:String):(String,Int,String,String,Option[String]) = {
    uri.stripPrefix(PREFIX).split("[/:]").toList match {
      case host :: port :: user :: pass :: tls :: _ => (host,port.toInt,user,pass,Option(tls))
      case host :: port :: user :: pass :: Nil => (host,port.toInt,user,pass,None)
      case host :: user :: pass :: Nil => (host,25,user,pass,None)
      case host :: Nil => (host,25,"","",None)
      case _ => ("mail",25,"","",None)
    }
  }
}