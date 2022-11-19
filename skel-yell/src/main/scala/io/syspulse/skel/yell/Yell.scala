package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._
import scala.collection.immutable

case class Yell(ts:Long,level:Int,area:String,text:String)

final case class Yells(users: immutable.Seq[Yell])

final case class YellCreateReq(level:Int,area:String,text:String)
final case class YellRandomReq()

final case class YellActionRes(status: String,id:Option[String])

final case class YellRes(user: Option[Yell])

object Yell {
  type ID = String
  def uid(y:Yell):ID = s"${y.ts}_${y.level}_${y.area}"
}