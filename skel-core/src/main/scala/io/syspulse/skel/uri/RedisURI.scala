package io.syspulse.skel.uri

/* 
redis://user:pass@host:port/db
*/
case class RedisURI(uri:String) {
  val PREFIX = "redis://"

  private val ((ruser:Option[String],rpass:Option[String]),(rhost:String,rport:Int),rdb:Int) = parse(uri)

  def db:Int = rdb
  def user:Option[String] = ruser
  def pass:Option[String] = rpass
  def host:String = rhost
  def port:Int = rport


  def parseCred(userPass:String) = userPass.split(":").toList match {
    case u :: p :: _ => (Some(u),Some(p))
    case u :: Nil => (Some(u),None)
  }

  def parseHost(hostPort:String) = hostPort.split(":").toList match {
    case h :: p :: _ => (h,p.toInt)
    case h :: Nil => (h,6379)
  }

  def parse(uri:String):((Option[String],Option[String]),(String,Int),Int) = {

    uri.stripPrefix(PREFIX).split("[@/]").toList match {
      case userPass :: hostPort :: db :: _ => (parseCred(userPass),parseHost(hostPort),db.toInt)
      case hostPort :: db :: Nil => ((None,None),parseHost(hostPort),db.toInt)
      case hostPort :: Nil => ((None,None),parseHost(hostPort),0)
      case _ => ((None,None),("localhost",6379),0)
    }
  }
}