package io.syspulse.skel.crypto.eth

import scala.util.{Try, Success, Failure}
import fastparse._

sealed trait Val {
  def value: Any
  def apply(i: Int): Val = this.asInstanceOf[Arr].value(i)
  def index(i: Int): Val = {    
    this match {
      case Arr(value @ _*) => 
        value(i)
      case Obj(value @ _*) =>        
        value(i)
      case RootVal(value) => 
        value.index(i)
      case _ => 
        this
    }
  }
  // def apply(s: String): Val =
  //   this.asInstanceOf[Obj].value.find(_._1 == s).get._2

  def extract(path: Seq[Path],depth: Int = 0): Val = {
    val v = index(path.head.value.head)
    if(path.size == 1 ) 
      return v
    // rucurse        
    v.extract(path.tail,depth+1)
  }
  
}

case class Value(value: String) extends Val {
  override def toString = s"$value".trim
}
case class RootVal(value: Val) extends Val {
  override def toString = value.toString.stripPrefix("(").stripSuffix(")")
}
case class Str(value: String) extends Val {
  override def toString = s"\"$value\""
}

// case class ObjNamed(value: (String, Val)*) extends AnyVal with Val
case class Obj(value: Val*) extends Val {
  override def toString = s"(${value.mkString(",")})"
}
case class Arr(value: Val*) extends Val {
  override def toString = s"[${value.mkString(",")}]"
}
case class Num(value: Double) extends Val

case object False extends Val{
  def value = false
}
case object True extends Val{
  def value = true
}
case object Null extends Val{
  def value = null
}


// -----------------------------------------------------------------------------------
sealed trait Path {
  def value: Seq[Int]
    
}
// case class IndexPath(value: Int) extends Path {
//   override def toString = s"Index($value)"
// }
case class ArrPath(value: Int*) extends Path {
  override def toString = s"Array[${value.mkString(",")}]"
}
case class ObjPath(value: Int*) extends Path {
  override def toString = s"List(${value.mkString(",")})"
}


// =============================================================================================================
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

  def integer[$: P] = P( integral ).!.map(
    x => x.toInt
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

  def array[$: P] = P( "[" ~/ expr.rep(sep=","./) ~ space ~ "]").map(Arr(_:_*))

  // def pair[$: P] = P( string.map(_.value) ~/ ":" ~/ expr )

  // def obj[$: P] =
  //   P( "{" ~/ pair.rep(sep=","./) ~ space ~ "}").map(Obj(_:_*))
  def obj[$: P] = P( "(" ~/ expr.rep(sep=","./) ~ space ~ ")").map(Obj(_:_*))

  // def expr[$: P]: P[Val] = P(
  //   space ~ (obj | array | string | `true` | `false` | `null` | number) ~ space
  // )

  def expr[$: P]: P[Val] = P(
    space ~ (obj | array | string | value) ~ space    
  )

  // def index[$: P] = P(  CharIn("+\\-").? ~ integral).!.map(
  //   x => IndexPath(x.toInt)
  // )
  def arrayPath[$: P] = P( "[" ~/ integer.rep(sep=","./) ~ space ~ "]").map(ArrPath(_:_*))
  def objPath[$: P] = P( "(" ~/ integer.rep(sep=","./) ~ space ~ ")").map(ObjPath(_:_*))
  def exprPath[$: P]: P[Seq[Path]] = P(
    space ~ (objPath | arrayPath ).rep(sep="."./) ~ space    
  )
  
  def parsePath(input: String): Try[Seq[Path]] = Try {
    val result = fastparse.parse(input, exprPath(_))
    result match {
      case fastparse.Parsed.Success(value, _) => value
      case fastparse.Parsed.Failure(_, _, _) => throw new IllegalArgumentException(s"Failed to parse path: $input")
    }
  }
  
  // Main extraction method
  def parse(input0: String): Try[Val] = Try {
    val input1 = input0.trim
    val simple = input1.startsWith("(") || input1.startsWith("[")
    val input = if(simple) input1 else s"(${input1})"
    val result = fastparse.parse(input, expr(_))
    result match {
      case fastparse.Parsed.Success(value0, _) => 
        val value = if(simple) value0 else RootVal(value0)
        value
      case fastparse.Parsed.Failure(_, _, _) => throw new IllegalArgumentException(s"Failed to parse: $input")
    }
  }
  
  def extract(input: String, path: String): Try[Val] = {
    if(path.isEmpty) return parse(input)

    for {
      parsed <- parse(input)
      path <- parsePath(path)
    } yield parsed.extract(path)
  }

  def extractString(input: String, path: String): Try[String] = {
    if(path.isEmpty) return Success(input)

    extract(input, path).map(_.toString)
  }
}

object SolidityResult extends SolidityResult {
  def apply(): SolidityResult = new SolidityResult()
}
