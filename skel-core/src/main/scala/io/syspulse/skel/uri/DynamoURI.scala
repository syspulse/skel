package io.syspulse.skel.uri

/* 
dynamo://server:8100/table
*/
case class DynamoURI(uri:String) {
  val PREFIX = "dynamo://"

  val (khost:String,ktable:String) = parse(uri)

  def host:String = "http://" + khost
  def table:String = ktable
  
  def parse(uri:String):(String,String) = {
    uri.stripPrefix(PREFIX).split("[/]").toList match {
      case host :: table :: _ => (host,table)
      case host :: Nil => (host,"table")
      case _ => ("","")
    }
  }
}