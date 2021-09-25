package io.syspulse.skel.flow

import com.typesafe.scalalogging.Logger

case class StageID(id:String)
object StageID {

  def apply(name:String,ts:Long):StageID = new StageID(s"${if(name.isBlank) "" else s"${name}-"}${ts.toString}")
  def apply(name:String=""):StageID = apply(name,System.currentTimeMillis)
}

abstract class Stage[F](name:String)(implicit errorPolicy:ErrorPolicy = new RepeatErrorPolicy()) {
  val log = Logger(s"${this}")

  def getName = name
  def getErrorPolicy = errorPolicy

  def exec(flow:Flow[F]):Flow[F]
  def start(flow:Flow[F]):Flow[F] = { flow }
  def stop(flow:Flow[F]):Flow[F] = { flow }
}
