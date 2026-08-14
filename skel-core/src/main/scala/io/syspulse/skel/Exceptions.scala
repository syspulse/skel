package io.syspulse.skel

abstract class Err(msg:String,code:Option[Int]=None) extends Exception(msg) {
  private val codeStr = code.getOrElse(math.abs(this.hashCode())).toString
  def getCode():String = codeStr
}

class ErrNotFound(msg:String) extends Err(msg,Some(Err.NOT_FOUND)) {
  override def toString = s"NotFound: ${msg}"
}

class ErrAuthorization(msg:String) extends Err(msg,Some(Err.AUTHORIZATION)) {
  override def toString = s"Authorization: ${msg}"
}

object Err {
  val NOT_FOUND = 40104
  val MISSING_PARAMETER = 40001
  val AUTHORIZATION = 40003
  val AUTHENTICATION = 40001
  val REJECTION = 40006
  val REQUEST_FAILED = 50001
  val METHOD_NOT_ALLOWED = 50005
  val INTERNAL_SERVER_ERROR = 50006 
}