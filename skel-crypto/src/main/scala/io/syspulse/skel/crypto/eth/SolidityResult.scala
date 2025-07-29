package io.syspulse.skel.crypto.eth

import scala.util.{Try, Success, Failure}
import fastparse._

sealed trait Val extends Any {
  def value: Any
  def apply(i: Int): Val = this.asInstanceOf[Arr].value(i)
  // def apply(s: java.lang.String): Val =
  //   this.asInstanceOf[Obj].value.find(_._1 == s).get._2
}
case class Value(value: java.lang.String) extends AnyVal with Val {
  override def toString = s"$value".trim
}
case class RootVal(value: Val) extends AnyVal with Val {
  override def toString = value.toString.stripPrefix("(").stripSuffix(")")
}
case class Str(value: java.lang.String) extends AnyVal with Val {
  override def toString = s"\"$value\""
}

// case class ObjNamed(value: (java.lang.String, Val)*) extends AnyVal with Val
case class Obj(value: Val*) extends AnyVal with Val {
  override def toString = s"(${value.mkString(",")})"
}
case class Arr(value: Val*) extends AnyVal with Val {
  override def toString = s"[${value.mkString(",")}]"
}
case class Num(value: Double) extends AnyVal with Val
case object False extends Val{
  def value = false
}
case object True extends Val{
  def value = true
}
case object Null extends Val{
  def value = null
}

class SolidityResult {
  import fastparse._
  import fastparse._, NoWhitespace._
  def stringChars(c: Char) = c != '\"' && c != '\\'  

  def space[$: P]         = P( CharsWhileIn(" \r\n", 0) )
  def digits[$: P]        = P( CharsWhileIn("0-9") )
  def exponent[$: P]      = P( CharIn("eE") ~ CharIn("+\\-").? ~ digits )
  def fractional[$: P]    = P( "." ~ digits )
  def integral[$: P]      = P( "0" | CharIn("1-9")  ~ digits.? )

  def number[$: P] = P(  CharIn("+\\-").? ~ integral ~ fractional.? ~ exponent.? ).!.map(
    x => Num(x.toDouble)
  )

  // def `null`[$: P]        = P( "null" ).map(_ => Null)
  // def `false`[$: P]       = P( "false" ).map(_ => False)
  // def `true`[$: P]        = P( "true" ).map(_ => True)

  def hexDigit[$: P]      = P( CharIn("0-9a-fA-F") )
  def unicodeEscape[$: P] = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
  def escape[$: P]        = P( "\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape) )

  def strChars[$: P] = P( CharsWhile(stringChars) )
  def string[$: P] =
    P( space ~ "\"" ~/ (strChars | escape).rep.! ~ "\"").map(Str.apply)

  def value[$: P] =
    P( space ~ CharsWhile(c => !c.isWhitespace && c != '\n' && c != '(' && c != ')' && c != '[' && c != ']' && c != ',').! ).map(Value.apply)

  // def values[$: P] =
  //   P( expr.rep(sep=","./) ).map(Obj(_:_*))

  def array[$: P] =
    P( "[" ~/ expr.rep(sep=","./) ~ space ~ "]").map(Arr(_:_*))

  // def pair[$: P] = P( string.map(_.value) ~/ ":" ~/ expr )

  // def obj[$: P] =
  //   P( "{" ~/ pair.rep(sep=","./) ~ space ~ "}").map(Obj(_:_*))
  def obj[$: P] =
     P( "(" ~/ expr.rep(sep=","./) ~ space ~ ")").map(Obj(_:_*))

  // def expr[$: P]: P[Val] = P(
  //   space ~ (obj | array | string | `true` | `false` | `null` | number) ~ space
  // )

  def expr[$: P]: P[Val] = P(
    space ~ (obj | array | string | value) ~ space    
  )
  
  // Main extraction method
  def parse(input0: String): Try[Val] = Try {
    val input1 = input0.trim
    val simple = input1.startsWith("(")
    val input = if(simple) input1 else s"(${input1})"
    val result = fastparse.parse(input, expr(_))
    result match {
      case fastparse.Parsed.Success(value0, _) => 
        val value = if(simple) value0 else RootVal(value0)
        value
      case fastparse.Parsed.Failure(_, _, _) => throw new IllegalArgumentException(s"Failed to parse input: $input")
    }
  }

  // def extractValue(input: String, path: String): Try[String] = {
  //   for {
  //     parsed <- parse(input)
      
  //   } yield r
  // }
}

object SolidityResult {
  def apply(): SolidityResult = new SolidityResult()
}
