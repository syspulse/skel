package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import java.time._
import java.time.format._
import java.time.temporal._
import java.util.Locale
import io.jvm.uuid._

import scala.util.Random

import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import java.time.{ZoneId,ZonedDateTime,LocalDateTime,Instant}
import java.time.format._

import scala.util.Using, java.nio.file.{Files, Paths, Path}, java.nio.charset.Charset
import java.nio.file.StandardOpenOption
import java.util.Base64

import scodec.bits.ByteVector

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileReader
import scala.util.Success
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.util.Failure
import scala.concurrent.ExecutionContext

object Util {
  
  implicit class HexStringInterpolator(val sc: StringContext) extends AnyVal {
    def h(args: Any*): Array[Byte] = { 
      val result = sc.s(args : _*)
      ByteVector.fromHex(result).orElse(Some(ByteVector.fromByte(0))).get.toArray
    }
  }

  val random = new SecureRandom
  val salt: Array[Byte] = Array.fill[Byte](16)(0x1f)
  val digest = MessageDigest.getInstance("SHA-256");  

  // ATTENTION: Never use seeds in Production !!!
  // This is only for testing 
  def generateRandomToken(seed:Option[String] = None,sz:Int = 32) = {
    val rnd = generateRandom(seed)
    Base64.getUrlEncoder.withoutPadding.encodeToString(rnd)
  }

  def generateRandom(seed:Option[String] = None,sz:Int = 32) = {
    seed match {
      case Some(seed) => 
        // use non-secure Random for deterministic tests
        val buf: Array[Byte] = Array.fill[Byte](sz)(0)
        new Random(Util.toHexString(seed.getBytes()).take("0x12345678".size).toLong).nextBytes(buf)
        buf
      case None => 
        // SecureRandom is non-deterministic, so seed only adds to existing seed
        val buf: Array[Byte] = Array.fill[Byte](sz)(0)
        random.nextBytes(buf)
        buf
    }
  }

  def fromHexString(h:String) = ByteVector.fromHex(h).orElse(Some(ByteVector.fromByte(0))).get.toArray
  def toHexString(b:Array[Byte]) = b.foldLeft("")((s,b)=>s + f"$b%02x")
  //def hex(x: Array[Byte],prefix:Boolean=true):String = s"""${if(prefix) "0x" else ""}${x.toArray.map("%02x".format(_)).mkString}"""
  def hex(x: Array[Byte],prefix:Boolean=true):String = s"""${if(prefix) "0x" else ""}${ByteVector(x).toHex}"""
  def unhex(h:String) = fromHexString(h)

  def SHA256(data:Array[Byte]):Array[Byte] = digest.digest(data)
  def SHA256(data:String):Array[Byte] = digest.digest(data.getBytes(StandardCharsets.UTF_8))
  def sha256(data:Array[Byte]):String = toHexString(digest.digest(data))
  def sha256(data:String):String = toHexString(digest.digest(data.getBytes(StandardCharsets.UTF_8)))
  
  val tsFormatSerde = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSSZ")
  val tsFormatLongest = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSS")
  val tsFormatLong = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ")
  val tsFormatYM = DateTimeFormatter.ofPattern("yyyy-MM")

  def now:String = tsFormatLongest.format(LocalDateTime.now)
  def now(fmt:String):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Instant.now.toEpochMilli), ZoneId.systemDefault).format(DateTimeFormatter.ofPattern(fmt))
  def toZoneDateTime(s:String,fmt:String) = ZonedDateTime.parse(s,DateTimeFormatter.ofPattern(fmt))
  def toZoneDateTime(s:String,fmt:DateTimeFormatter = tsFormatLong) = ZonedDateTime.parse(s,fmt)
  def timestamp(ts:Long,fmt:String,zone:ZoneId = ZoneId.systemDefault):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zone).format(DateTimeFormatter.ofPattern(fmt))
  //def timestamp(ts:Long,fmt:String):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault).format(DateTimeFormatter.ofPattern(fmt))

  def tsToString(ts:Long) = ZonedDateTime.ofInstant(
      Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatLong)

  def tsToStringYearMonth(ts:Long = 0L) = ZonedDateTime.ofInstant(
      if(ts==0L) Instant.now else Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatYM)
  
  // time is delimited with {}
  def toFileWithTime(fileName:String,ts:Long=System.currentTimeMillis()) = {
    val tss = fileName.split("[{]").filter(_.contains("}")).map(s => s.substring(0,s.indexOf("}")))
    val tssPairs = tss.map(s => (s,timestamp(ts,s)))

    tssPairs.foldLeft(fileName)( (fileName,pair) => { fileName.replace("{"+pair._1+"}",pair._2) })    
  }

  def nextTimestampDir(fileName:String,ts:Long=System.currentTimeMillis()) = {
    nextTimestampFile(extractDirWithSlash(fileName),ts)
  }

  def nextTimestampFile(fullName:String,ts:Long=System.currentTimeMillis()) = {
    val tss = fullName.split("[{]").filter(_.contains("}")).map(s => s.substring(0,s.indexOf("}")))
    val deltas = tss.reverse.map(s => s match {
      case "ss" | "s" =>          (0,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusSeconds(1).toInstant().toEpochMilli())
      case "mm" | "m" =>          (1,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusMinutes(1).withSecond(0).toInstant().toEpochMilli())
      case "HH" | "H" =>          (2,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusHours(1).withMinute(0).withSecond(0).toInstant().toEpochMilli())
      case "dd" | "d" =>          (3,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusDays(1).withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli())
      case "MM" | "M" =>          (4,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli())
      case "yyyy" | "yy" | "y" => (5,ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), 
                                     ZoneId.systemDefault).plusYears(1).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli())
    }).toList

    //println(s"${deltas}")
    //println(s"${deltas.sortBy(_._1)}")
    deltas.sortBy(_._1).map(_._2).headOption.getOrElse(0L)
  }

  def getParentUri(uri:String) = {
    val s = uri.stripSuffix("/").split("/")
    s.take(s.size - 1).mkString("/")
  }

  // Java11: use isBlank
  def toDirWithSlash(dir:String):String = 
    if(dir.trim.isEmpty) dir else if(dir.trim.endsWith("/")) dir else dir + "/"

  def extractDirWithSlash(dir:String):String = {
    if(dir.trim.isEmpty) 
      "" 
    else if(dir.trim.endsWith("/")) 
      dir 
    else 
      getParentUri(dir) + "/"
  }

  def info = {
    val p = getClass.getPackage
    val name = p.getImplementationTitle
    val version = p.getImplementationVersion
    (name,version)
  }

  def uuid(id:String,entityName:String=""):UUID = {
    val bb = Util.SHA256(entityName).take(4) ++  Array.fill[Byte](2+2+2)(0) ++ Util.SHA256(id).take(6)
    UUID(bb)
  }

  val UUID_0 = UUID(Array.fill[Byte](16)(0))

  def getHostPort(address:String):(String,Int) = { 
    val (host,port) = address.split(":").toList match{ 
      case h::p => (h,p(0))
      case _ => (address,"0")
    }
    (host,port.toInt)
  }

  def rnd(limit:Double) = Random.between(0,limit)

  def csvToList(s:String,dList:String=";") = s
    .split(dList)
    .map(_.trim)
    .filter(s => !s.isEmpty() && s != "\"\"")
    .toList
  
  def toCSV(o:Product,d:String=",",dList:String=";"):String = toCsv(o,d,dList)

  def toCsv(o:Product,d:String,dList:String):String = {
    //o.productIterator.foldRight("")(_.toString + "," + _.toString).stripSuffix(",")
    o.productIterator.map{
      case p: Product => {
        if(p.isInstanceOf[List[_]]) {
          val s = toCSV(p,dList,dList)
          s.stripSuffix(dList)
        } 
          else toCSV(p,d,dList)
      }
      case pp => 
        if(pp.isInstanceOf[Array[_]]) {
          val arr = pp.asInstanceOf[Array[_ <: Product]].toList    
          val s = toCSV(arr.asInstanceOf[Product],dList,dList)
          s.stripSuffix(dList)
        } else
          pp
    }.mkString(d)
  }

  //import scala.reflect.runtime.universe._
  // this does not work and needs type tags information
  def isCaseClass(v: Any): Boolean = {
     val typeMirror = scala.reflect.runtime.universe.runtimeMirror(v.getClass.getClassLoader)
     val instanceMirror = typeMirror.reflect(v)
     val symbol = instanceMirror.symbol
     symbol.isCaseClass
  }

  // sqlWrap tells to wrap string into ''
  def traverseAny(a:Any):Array[(String,String)] = {
    val ff = a.getClass.getDeclaredFields.map( v => (v.getName,v))
    ff.map { case(n,f) => {
      f.setAccessible(true)
      val typeName = f.getGenericType.getTypeName.toString
      if(typeName.startsWith("scala.collection")) {
        val o = f.get(a)
        val vv:Array[(String,String)] = o.asInstanceOf[Seq[_]].map(v => traverseAny(v)).toArray.flatten
        vv
      } else {
        val v = f.get(a)
        if(v!=null 
          && !v.getClass.isPrimitive 
          && !v.isInstanceOf[java.lang.Byte]
          && !v.isInstanceOf[java.lang.Integer]
          && !v.isInstanceOf[java.lang.Long]
          && !v.isInstanceOf[java.lang.Short]
          && !v.isInstanceOf[java.lang.Boolean]
          && !v.isInstanceOf[java.lang.Double]
          && !v.isInstanceOf[java.lang.String]
        )
          traverseAny(v)
        else
          Array[(String,String)]((n,
            if(v!=null) 
              v.toString 
            else 
              "null"
          ))
      }
    }}.flatten
  }

  def traverseAnySQL(a:Any):Array[(String,String)] = {
    val ff = a.getClass.getDeclaredFields.map( v => (v.getName,v))
    ff.map { case(n,f) => {
      f.setAccessible(true)
      val typeName = f.getGenericType.getTypeName.toString
      if(typeName.startsWith("scala.collection")) {
        val o = f.get(a)
        val vv:Array[(String,String)] = o.asInstanceOf[Seq[_]].map(v => traverseAny(v)).toArray.flatten
        vv
      } else {
        val v = f.get(a)
        if(v!=null 
          && !v.getClass.isPrimitive 
          && !v.isInstanceOf[java.lang.Byte]
          && !v.isInstanceOf[java.lang.Integer]
          && !v.isInstanceOf[java.lang.Long]
          && !v.isInstanceOf[java.lang.Short]
          && !v.isInstanceOf[java.lang.Boolean]
          && !v.isInstanceOf[java.lang.Double]
          && !v.isInstanceOf[java.lang.String]
        )
          traverseAny(v)
        else
          Array[(String,String)]((n, {            
            if(v!=null)
              if(v.isInstanceOf[String])
                s"'${v.toString}'"
              else
                v.toString
            else
              if(v.isInstanceOf[String])
                "NULL"
              else
                "null"
          }))
      }
    }}.flatten
  }
  
  
  def toFlatData(o:Product,sep:String=":"):String = {
    val mm = traverseAny(o)
    mm.foldRight("")(_._2 + sep + _).stripSuffix(sep)
  }
  
  def writeToFile(fileName:String,lines:Seq[String]) = 
    Using(Files.newBufferedWriter(Paths.get(fileName), Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      writer => lines.foreach(line => writer.write(s"${line}\n"))
  }

  def appendToFile(fileName:String,lines:Seq[String]) = 
    Using(Files.newBufferedWriter(Paths.get(fileName), Charset.forName("UTF-8"), 
          StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      writer => lines.foreach(line => writer.write(s"${line}\n"))
  }

  def stdin(fun:(String) => Boolean) = {
    var stdin = ""
    while ({stdin = scala.io.StdIn.readLine(); stdin != null}) {
      val r = fun(stdin)
      if(!r) stdin = null
    }
  }

  case class Top(cpu:Int,freeMem:Long,maxMem:Long,totalMem:Long)
  def top() = {
    Top(
      Runtime.getRuntime().availableProcessors(),
      Runtime.getRuntime().freeMemory(),
      Runtime.getRuntime().maxMemory(),
      Runtime.getRuntime().totalMemory()
    )
  }
  
  import scala.util.Using
  def loadFile(path:String):Try[String] = {
    if(path.trim.startsWith("classpath:")) {
      Using( new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(path.stripPrefix("classpath:"))))) { reader => 
        reader.lines().toArray.mkString(System.lineSeparator())
      }
    } else
    {
      Success(os.read(os.Path(path,os.pwd)))
    }
  }

  def pathToFullPath(path:String):String = {
    if(path.trim.startsWith("/")) return path
    s"${os.pwd.toString}/${path}"
  }

  def timed[C](code: => C)(implicit log:Logger): C = {
    val ts0 = System.nanoTime
    val r = code
    val ts1 = System.nanoTime
    log.info(s"Elapsed: ${Duration.ofNanos(ts1 - ts0).toMillis()} msec")
    r
  }

  def timed[C](n:Int = 0)(code: => C)(implicit log:Logger): Unit = {
    val ts0 = System.nanoTime
    var r = null
    for(i <- Range(0,n)) {      
      code      
    }
    val ts1 = System.nanoTime      
    log.info(s"Elapsed: ${Duration.ofNanos(ts1 - ts0).toMillis()} msec")    
  }

  def replaceVar(expr0:String,vars:Map[String,Any]):String = {
    // special case for file patterns
    val expr = if(expr0.startsWith("file://") || expr0.startsWith("dir://") || expr0.startsWith("dirs://")) 
      try {
        toFileWithTime(expr0)
      } catch {
        case e:Exception => 
          // ignore error since it can be a variable
          expr0
      }
    else 
      expr0

    val rexpr = """(\{[a-zA-Z_\.-]+\})""".r
    val pairs = rexpr.findAllIn(expr).flatMap( v =>{
      val variable = v.substring(1,v.size-1)      
      val vv = vars.collect{ case(n,value) if(n == variable) => value}
      vv.headOption.map(value => (variable,value))

    })
    val expr1 = pairs.foldLeft(expr)((e,p) => {
      val r = "\\{"+p._1+"\\}"
      e.replaceAll(r,p._2.toString)
    })
    expr1
  }

  def replaceEnvVar(expr:String,env:Map[String,String] = sys.env):String = {
    if(!expr.contains('{'))
      return expr

    val i0 = expr.indexOf('{')
    if(i0 == -1)
      return expr
    val i1 = expr.indexOf('}')
    if(i1 == -1)
      return expr
    
    val v = expr.substring(i0+1,i1)
    val ev = env.get(v)
    
    if(!ev.isDefined)
      return expr
    
    expr.take(i0) + ev.get + expr.drop(i0 + 1 + 1 + v.size)
  }

  // more reliable BigInt converter of format is into double
  def toBigInt(v:String):BigInt = {
    if(v.startsWith("0x")) 
      BigInt(v.drop(2),16)
    else
    if(v.contains(".")) 
      java.math.BigDecimal.valueOf(v.toDouble).toBigInteger
    else
      BigInt(v)
  }
  
  def isUUID(s:String) = s.matches("""\d{8}-\d{4}-\d{4}-\d{4}-\d{12}""")

  // Future lifter
  // https://stackoverflow.com/a/29344937
  def lift[T](futures: Seq[Future[T]])(implicit ec:ExecutionContext) = futures.map(_.map { Success(_) }.recover { case t => Failure(t) })
  def waitAll[T](futures: Seq[Future[T]])(implicit ec:ExecutionContext) = Future.sequence(lift(futures))

  // Primitive jq-style Json parser
  def parseJson(json:String,route:String):Try[Seq[String]] = {
        
    def parseRoute(j:ujson.Value,r:String):Seq[String] = {
      val i = r.indexOf(".")
      val (v,rest) = if(i == -1) (r,"") else (r.substring(0,i),r.substring(i+1))
      (v,rest) match {
        case (expr,"") if(expr.endsWith("[]")) =>
          j(expr.stripSuffix("[]"))
            .arr
            .map(j => j.str)
            .toSeq

        case (expr,"") => 
          if(expr == j.str) Seq(expr)
          else Seq()
        case (expr,rest) if(expr.endsWith("[]")) =>
          j(expr.stripSuffix("[]"))
            .arr
            .map(j => parseRoute(j,rest))
            .flatten
            .toSeq
        case (expr,rest) =>
          parseRoute(j.obj(expr),rest)            
      }
    }

    try {
      val j = ujson.read(json)
      Success(parseRoute(j,route))
    } catch {
      case e:Exception => Failure(e)
    }
  }

  // prints Array[_] (Java Array) in a nice scala way
  private def pprintArray(obj: Any, paramName: Option[String] = None): String = {
    
    obj match {
      case seq: Array[Any] =>
        val array = seq.map(Util.pprintArray(_)).mkString(",")
        s"Array(${array})"
      case seq: Iterable[Any] =>
        val ii = seq.map(Util.pprintArray(_)).mkString(",")
        s"${seq.getClass().getSimpleName()}(${ii})"
      case None => "None"
      case obj: Product =>
        val name = obj.getClass().getSimpleName()
        val data = (obj.productIterator zip obj.productElementNames)
          .map { case (subObj, paramName) => Util.pprintArray(subObj, Some(paramName)) }
          .mkString(",")
        s"${name}(${data})"
      case _ => obj.toString
    }
  }

  def toStringWithArray(obj:Any) = {    
    pprintArray(obj)
  }

  def succeed[T](code: => T):Try[T] = {
    try {
      Success(
        code
      )
    } catch {
      case e:Exception => Failure(e)
    }
  }
}

