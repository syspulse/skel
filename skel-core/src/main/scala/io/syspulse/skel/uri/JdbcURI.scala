package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

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

jdbc:async//db1
jdbc:postgres:async//db1

*/
case class JdbcURI(uri:String) {
  val PREFIX = "jdbc://"

  // Parse URI and extract query parameters
  private val (baseUri, params) = uri.split("\\?", 2) match {
    case Array(base, query) =>
      val params = query.split("&").map { param =>
        param.split("=", 2) match {
          case Array(key, value) => (key, value)
          case Array(key) => (key, "")
        }
      }.toMap
      (base, params)
    case Array(base) => (base, Map.empty[String, String])
  }

  private val (rdbType:String,(ruser:Option[String],rpass:Option[String]),(rhost:String,rport:Int),rdb:Option[String],rdbConfig:Option[String],rasync:Boolean) =
    parse(baseUri)

  def dbType:String = rdbType
  // if db is defined, then dbCondfig is not valid
  def db:Option[String] = rdb
  // defined dbConfig contains everything (user/pass/type/url/database)
  def dbConfig:Option[String] = rdbConfig
  def user:Option[String] = ruser
  def pass:Option[String] = rpass
  def host:String = rhost
  def port:Int = rport
  def async:Boolean = rasync

  // Extract timezone from query parameters
  def timezone:Option[String] = params.get("TimeZone").orElse(params.get("timezone"))
  def opts:Map[String,String] = params

  // Construct proper JDBC URL for DriverManager or jasync-sql
  // Maps generic dbType to actual JDBC driver name (postgres -> postgresql)
  // If async=true, returns jasync-sql format (without jdbc: prefix and query parameters)
  // If async=false, returns standard JDBC URL format
  def getJdbcUrl(async: Boolean = false): String = {
    val driverName = dbType match {
      case "postgres" => "postgresql"
      case other => other
    }
    
    // Build user:pass@ part if credentials are available (only for async jasync-sql format)
    val credentials = if (async) {
      (user, pass) match {
        case (Some(u), Some(p)) => s"${u}:${p}@"
        case (Some(u), None) => s"${u}@"
        case _ => ""
      }
    } else {
      "" // JDBC URLs don't include credentials in the URL string
    }
    
    val baseUrl = db match {
      case Some(database) => s"jdbc:${driverName}://${credentials}${host}:${port}/${database}"
      case None => dbConfig match {
        case Some(config) => s"jdbc:${driverName}://${credentials}${host}:${port}/${config}"
        case None => s"jdbc:${driverName}://${credentials}${host}:${port}"
      }
    }

    // Append query parameters if present (only for sync JDBC URLs)
    val url = if (params.nonEmpty && !async) {
      val queryString = params.map { case (k, v) => s"${k}=${v}" }.mkString("&")
      s"${baseUrl}?${queryString}"
    } else {
      baseUrl
    }

    val finalUrl = Util.replaceEnvVar(url)
    
    // Convert to jasync-sql format if async=true
    if (async) {
      // Remove jdbc: prefix and query parameters for jasync-sql
      finalUrl.replaceFirst("^jdbc:", "").split("\\?")(0)
    } else {
      finalUrl
    }
  }
  

  def parseCred(userPass:String) = userPass.split(":").toList match {
    case u :: p :: _ => (Util.resolveEnvVar(u),Util.resolveEnvVar(p))
    case u :: Nil => (Util.resolveEnvVar(u),None)
  }

  def parseHost(hostPort:String) = hostPort.split(":").toList match {
    case h :: p :: _ => (h,p.toInt)
    case h :: Nil => (h,5432)
  }

  def parseDbType(dbType:String) = dbType.split(":").toList match {
    case dbt :: "async" :: _ => (dbt,true)
    case dbt :: Nil => (dbt,false)
  }

  def parse(uri:String):(String,(Option[String],Option[String]),(String,Int),Option[String],Option[String],Boolean) = {
    uri.split("://|[@/]").toList  match {

      case "jdbc" :: "postgresql" :: userPass :: hostPort :: db :: Nil => ("postgres",parseCred(userPass),parseHost(hostPort),Some(db),None,false)
      case "jdbc" :: "postgresql" ::hostPort :: db :: Nil => ("postgres",(None,None),parseHost(hostPort),Some(db),None,false)
      case "jdbc" :: "postgresql" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),false)
      case "jdbc" :: "postgresql" :: Nil => ("postgres",(None,None),("localhost",5432),None,Some("postgres"),false)
      case "jdbc" :: "mysql" :: userPass :: hostPort :: db :: Nil => ("mysql",parseCred(userPass),parseHost(hostPort),Some(db),None,false)
      case "jdbc" :: "mysql" :: hostPort :: db :: Nil => ("mysql",(None,None),parseHost(hostPort),Some(db),None,false)
      
      case "jdbc" :: "mysql" :: dbConfig :: Nil => ("mysql",(None,None),("localhost",3306),None,Some(dbConfig),false)
      case "jdbc" :: "mysql" :: Nil => ("mysql",(None,None),("localhost",3306),None,Some("mysql"),false)
      case "jdbc" :: "postgres" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),false)
      case "jdbc" :: "postgres" :: Nil => ("postgres",(None,None),("localhost",3306),None,Some("postgres"),false)

      case "jdbc:mysql" :: dbConfig :: Nil => ("mysql",(None,None),("localhost",3306),None,Some(dbConfig),false)
      case "jdbc:mysql" :: Nil => ("mysql",(None,None),("localhost",3306),None,Some("mysql"),false)
      case "jdbc:postgres" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),false)
      case "jdbc:postgres" :: Nil => ("postgres",(None,None),("localhost",3306),None,Some("postgres"),false)

      case "jdbc:mysql:async" :: dbConfig :: Nil => ("mysql",(None,None),("localhost",3306),None,Some(dbConfig),true)
      case "jdbc:mysql:async" :: Nil => ("mysql",(None,None),("localhost",3306),None,Some("mysql"),true)
      case "jdbc:postgres:async" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),true)
      case "jdbc:postgres:async" :: Nil => ("postgres",(None,None),("localhost",3306),None,Some("postgres"),true)
      
      case "jdbc:async" :: userPass :: hostPort :: db :: Nil => ("postgres",parseCred(userPass),parseHost(hostPort),Some(db),None,true)
      case "jdbc:async" :: hostPort :: db :: Nil => ("postgres",(None,None),parseHost(hostPort),Some(db),None,true)
      case "jdbc:async" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),true)
      case "jdbc:async" :: Nil => ("postgres",(None,None),("localhost",5432),None,Some("postgres"),true)

      case "jdbc" :: userPass :: hostPort :: db :: Nil => ("postgres",parseCred(userPass),parseHost(hostPort),Some(db),None,false)
      case "jdbc" :: hostPort :: db :: Nil => ("postgres",(None,None),parseHost(hostPort),Some(db),None,false)
      case "jdbc" :: dbConfig :: Nil => ("postgres",(None,None),("localhost",5432),None,Some(dbConfig),false)
      case "jdbc" :: Nil => ("postgres",(None,None),("localhost",5432),None,Some("postgres"),false)
      
      
      // style postgres:// or mysql://
      case dbType :: userPass :: hostPort :: db :: Nil => 
        val (dbt,async) = parseDbType(dbType)
        (dbt,parseCred(userPass),parseHost(hostPort),Some(db),None,async)
      case dbType :: hostPort :: db :: Nil =>
        val (dbt,async) = parseDbType(dbType) 
        (dbt,(None,None),parseHost(hostPort),Some(db),None,async)
      case dbType :: dbConfig :: Nil => 
        val (dbt,async) = parseDbType(dbType)
        (dbt,(None,None),("localhost",5432),None,Some(dbConfig),async)
      case dbType :: Nil => 
        val (dbt,async) = parseDbType(dbType)
        (dbt,(None,None),("localhost",5432),None,Some("postgres"),async)
      
    }
  }
}