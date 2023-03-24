package io.syspulse.skel.uri

/* 
parq://APPEND:dir/file.parq.{snappy,lz4,gzip,zstd}
*/

case class ParqURI(uri:String) {
  
  private val (emode:String,efile:String,ezip:String) = parse(uri)

  def mode:String = emode
  def file:String = efile
  def zip:String = ezip

  def getZip(path:String):String = path.split("\\.").lastOption.getOrElse("parq").toLowerCase

  def parse(uri:String):(String,String,String) = {
    
    uri.split("://|:").toList match {
      case "parq" :: mode :: path :: Nil => (mode.toUpperCase,path,getZip(path))
      case "parq" :: path :: Nil => ("OVERWRITE",path,getZip(path))
      case mode :: path :: Nil => (mode.toUpperCase,path,getZip(path))
      case path :: Nil => ("OVERWRITE",path,getZip(path))
      case _ => throw new Exception(s"invalid uri: '${uri}'")
    }
  }
}