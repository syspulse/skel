package io.syspulse.skel.notify.email

/* 
smtp://smtp.gmail.com:587/user@pass
*/
case class SmtpURI(uri:String) {
  val PREFIX = "smtp://"

  val (shost:String,sport:Int,suser:String,spass:String) = parse(uri)

  def host:String = shost
  def port:Int = sport
  def user:String = suser
  def pass:String = spass

  def parse(uri:String):(String,Int,String,String) = {
    uri.stripPrefix(PREFIX).split("[/:@]").toList match {
      case host :: port :: user :: pass :: _ => (host,port.toInt,user,pass)
      case host :: user :: pass :: Nil => (host,25,user,pass)
      case host :: Nil => (host,25,"","")      
    }
  }
}