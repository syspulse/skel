package io.syspulse.skel.uri

/* 

If host is specified, it must contain Database !

Standard JDBC Uri is supported:  jdbc:postgresql://user:pass@localhost:5432/ingest_db

Full DB uri:

postgres://localhost:5432/ingest_db
jdbc://localhost:5432/ingest_db
jdbc://user:pass@localhost:5432/ingest_db

Config DB uri:

jdbc://db1
jdbc:postgres://db1
jdbc:mysql://db1
jdbc://mysql://db1
jdbc://postgres://db1
postgres://db1
mysql://db1

*/
case class JdbcURI(uri:String) {
  val PREFIX = "jdbc://"

  private val (rdbType:String,(ruser:Option[String],rpass:Option[String]),(rhost:String,rport:Int),rdb:Option[String],rdbConfig:Option[String]) = parse(uri)

  def dbType:String = rdbType     
  // if db is defined, then dbCondfig is not valid
  def db:Option[String] = rdb
  // defined dbConfig contains everything (user/pass/type/url/database)
  def dbConfig:Option[String] = rdbConfig
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
    case h :: Nil => (h,5432)
  }

  def parse(uri:String):(String,(Option[String],Option[String]),(String,Int),Option[String],Option[String]) = {
    uri.split("://|[@/]").toList  match {

      case "jdbc" :: "postgresql" :: userPass :: hostPort :: db :: Nil => ("postgres",parseCred(userPass),parseHost(hostPort),Some(db),None)
      case "jdbc" :: "postgresql" ::hostPort :: db :: Nil => ("postgres",(None,None),parseHost(hostPort),Some(db),None)
      case "jdbc" :: "postgresql" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig))
      case "jdbc" :: "postgresql" :: Nil => ("postgres",(None,None),("localhost",5432),None,Some("postgres"))
      case "jdbc" :: "mysql" :: userPass :: hostPort :: db :: Nil => ("mysql",parseCred(userPass),parseHost(hostPort),Some(db),None)
      case "jdbc" :: "mysql" :: hostPort :: db :: Nil => ("mysql",(None,None),parseHost(hostPort),Some(db),None)
      
      case "jdbc" :: "mysql" :: dbConfig :: Nil => ("mysql",(None,None),("localhost",3306),None,Some(dbConfig))
      case "jdbc" :: "mysql" :: Nil => ("mysql",(None,None),("localhost",3306),None,Some("mysql"))
      case "jdbc" :: "postgres" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig))
      case "jdbc" :: "postgres" :: Nil => ("postgres",(None,None),("localhost",3306),None,Some("postgres"))

      case "jdbc:mysql" :: dbConfig :: Nil => ("mysql",(None,None),("localhost",3306),None,Some(dbConfig))
      case "jdbc:mysql" :: Nil => ("mysql",(None,None),("localhost",3306),None,Some("mysql"))
      case "jdbc:postgres" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig))
      case "jdbc:postgres" :: Nil => ("postgres",(None,None),("localhost",3306),None,Some("postgres"))
      
      case "jdbc" :: userPass :: hostPort :: db :: Nil => ("postgres",parseCred(userPass),parseHost(hostPort),Some(db),None)
      case "jdbc" :: hostPort :: db :: Nil => ("postgres",(None,None),parseHost(hostPort),Some(db),None)
      case "jdbc" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig))
      case "jdbc" :: Nil => ("postgres",(None,None),("localhost",5432),None,Some("postgres"))
      
      // style postgres:// or mysql://
      case dbType :: userPass :: hostPort :: db :: Nil => (dbType,parseCred(userPass),parseHost(hostPort),Some(db),None)
      case dbType :: hostPort :: db :: Nil => (dbType,(None,None),parseHost(hostPort),Some(db),None)
      case dbType :: dbConfig :: Nil => (dbType,(None,None),("localhost",5432),None,Some(dbConfig))
      case dbType :: Nil => (dbType,(None,None),("localhost",5432),None,Some("postgres"))
      
    }
  }
}