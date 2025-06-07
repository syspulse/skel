package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,data:List[Data])

class UtilSpec extends AnyWordSpec with Matchers {
  
  val DST = ZonedDateTime.now.getZone().getRules.isDaylightSavings(ZonedDateTime.now.toInstant)

  "Util" should {

    "sha256 should be 32 bytes" in {
      val bb = Util.SHA256("US")
      bb.size should === (32)
    }

    "convert (US,Country) to the same uuid" in {
      val uuid0 = UUID("aff64e4f-0000-0000-0000-9b202ecbc6d4")
      val uuid1 = Util.uuid("US","country")
      uuid1 should === (uuid0)
      val uuid2 = Util.uuid("US","country")
      uuid2 should === (uuid0)
    }

    "not (UK,Country) equal to (US,Country)" in {
      val uuid1 = Util.uuid("US","country")
      val uuid2 = Util.uuid("UK","country")
      uuid1 should !== (uuid2)
    }

    "convert now timestamp to YYYY-mm string with correct year and month" in {
      val ym = Util.tsToStringYearMonth()
      val lm = LocalDateTime.now
      ym should === (String.format("%d-%02d",lm.getYear,lm.getMonthValue))
    }

    "convert 'file-{YYYY}' to 'file-2021'" in {
      val s = Util.toFileWithTime("file-{YYYY}")
      s should === (s"file-${LocalDateTime.now.getYear}")
    }

    "convert 'file-{YYYY}.log' to 'file-2021.log'" in {
      val s = Util.toFileWithTime("file-{YYYY}.log")
      s should === (s"file-${LocalDateTime.now.getYear}.log")
    }

    "convert 'file-{yyyy-MM-dd-HH:mm:ss}-suffix.log' to file with timestamp" in {
      val s = Util.toFileWithTime("file-{yyyy-MM-dd_HH:mm:ss}-suffix.log")
      val t = LocalDateTime.now
      s should === ("file-%d-%02d-%02d_%02d:%02d:%02d-suffix.log".format(t.getYear,t.getMonthValue,t.getDayOfMonth,t.getHour,t.getMinute,t.getSecond))
    }

    "convert '/dir/year={yyyy}/month={MM}/day={dd}' to Hive path" in {
      val s = Util.toFileWithTime("/dir/year={yyyy}/month={MM}/day={dd}")
      val t = LocalDateTime.now
      s should === ("/dir/year=%d/month=%02d/day=%02d".format(t.getYear,t.getMonthValue,t.getDayOfMonth))
    }

    "produce CSV '5,100000000000,Text,7.13' for case class" in {
      case class Data(i:Int,l:Long,s:String,d:Double)
      val c = Data(5,100000000000L,"Text",7.13)
      val csv = Util.toCSV(c)
      csv should === ("5,100000000000,Text,7.13")
    }

    "produce CSV '5,100000000000,,7.13' for case class" in {
      case class Data(i:Option[Int],l:Long,s:Option[String] = None,d:Option[Double]=None)
      val c = Data(Some(5),100000000000L,None,Some(7.13))
      val csv = Util.toCSV(c)
      csv should === ("5,100000000000,,7.13")
    }

    "toDirWithSlash('') return ''" in {
      val s = Util.toDirWithSlash("")
      s should === ("")
    }

    "toDirWithSlash('data/') return 'data/'" in {
      val s = Util.toDirWithSlash("data/")
      s should === ("data/")
    }

    "toDirWithSlash('data') return 'data/'" in {
      val s = Util.toDirWithSlash("data")
      s should === ("data/")
    }

    "toDirWithSlash('/data') return '/data/'" in {
      val s = Util.toDirWithSlash("/data")
      s should === ("/data/")
    }

    "toFlatData for Complex Class produce 'measure-0:10:1.0:m/s:20:2.0:kg'" in {
      val dd = DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg"))))
      val s = Util.toFlatData(dd)
      s should === ("measure-0:10:1.0:m/s:20:2.0:kg")
    }

    "AccessToken should be 32 bytes" in {
      val bb = Util.generateRandomToken()
      info(bb)
      bb.size should === (43)
    }

    "load file from classpath:/resource-1.conf" in {
      val txt = Util.loadFile("classpath:/resource-1.conf")
      
      txt should === (Success("data"))
      txt.get.size should === (4)
    }

    "load file from conf/resource-2.conf (in project root ./conf/)" in {
      val txt = Util.loadFile("conf/resource-2.conf")
      
      txt should === (Success("data2"))
      txt.get.size should === (5)
    }

    "extractDirWithSlash('') return ''" in {
      val s = Util.extractDirWithSlash("")
      s should === ("")
    }

    "extractDirWithSlash('/data/file.log') return '/data/'" in {
      val s = Util.extractDirWithSlash("/data/file.log")
      s should === ("/data/")
    }

    "extractDirWithSlash('/data/') return '/data/'" in {
      val s = Util.extractDirWithSlash("/data/")
      s should === ("/data/")
    }

    "extractDirWithSlash('/dir1/dir2/file.log') return '/dir1/dir2/'" in {
      val s = Util.extractDirWithSlash("/dir1/dir2/file.log")
      s should === ("/dir1/dir2/")
    }

    "extractDirWithSlash('dir1/dir2/file.log') return 'dir1/dir2/'" in {
      val s = Util.extractDirWithSlash("dir1/dir2/file.log")
      s should === ("dir1/dir2/")
    }


    "nextTimestamp '/dir/year={yyyy}/month={MM}/day={dd}/file.log' should be next day" in {
      //System.setProperty("user.timezone", "UTC");
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/month={MM}/day={dd}/file.log",t1)      
      val f2 = Util.toFileWithTime("{dd}",t2)      
      f2 should === ("13")
    }

    "nextTimestamp '/dir/year={yyyy}/month={MM}/file.log' should be next month" in {
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/month={MM}/file.log",t1)      
      val f2 = Util.toFileWithTime("{MM}",t2)      
      f2 should === ("10")
    }

    "nextTimestamp '/dir/year={yyyy}/file.log' should be next year" in {
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/file.log",t1)      
      val f2 = Util.toFileWithTime("{yyyy}",t2)      
      f2 should === ("2023")
    }

    // Fails due to DST
    "nextTimestamp '/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/file.log' should be next hour" ignore {      
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/file.log",t1)
      val f2 = Util.toFileWithTime("{HH}",t2)
      // info(s"${Util.toFileWithTime("{HH}",t1)} -- ${Util.toFileWithTime("{HH}",t2)}")    
      if(DST) f2 should === ("15") else f2 should === ("14")
    }

    "nextTimestamp '/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/min=${mm}/file.log' should be next minute" in {
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/min=${mm}/file.log",t1)
      val f2 = Util.toFileWithTime("{mm}",t2)
      f2 should === ("41")
    }

    // Fails due to DST
    "nextTimestamp '/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/file-${HH}-${mm}.log' should be next hour (ignore file)" ignore {
      val t1 = 1662982804209L 
      val t2 = Util.nextTimestampDir("/dir/year={yyyy}/month={MM}/day={dd}/hour=${HH}/file-${HH}-${mm}.log",t1)
      val f2 = Util.toFileWithTime("{HH}",t2)
      //f2 should === ("15")
      if(DST) f2 should === ("15") else f2 should === ("14")
    }

    "nextTimestampFile '/dir/file-{mm}.log' should be next minute" in {
      val t1 = System.currentTimeMillis()
      val t2 = Util.nextTimestampFile("/dir/file-{mm}.log",t1)
      
      val f2 = Util.toFileWithTime("{mm}",t2)
      val t3 = "%02d".format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t1), ZoneId.systemDefault).plusMinutes(1).getMinute())
      f2 should === (t3)
    }

    "nextTimestampFile '/dir/{HH}/file-{mm}.log' should be next minute and not hour" in {
      val t1 = System.currentTimeMillis()
      val t2 = Util.nextTimestampFile("/dir/{HH}/file-{mm}.log",t1)
      
      val f2 = Util.toFileWithTime("{mm}",t2)
      val t3 = "%02d".format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t1), ZoneId.systemDefault).plusMinutes(1).getMinute())
      f2 should === (t3)
    }

    "nextTimestampFile '/dir/file.log' == 0" in {
      val t1 = System.currentTimeMillis()
      val t2 = Util.nextTimestampFile("/dir/file.log",t1)
      
      t2 should === (0L)
    }

    "produce CSV 'data,attr1;attr2,10' for case class with List(1,2)" in {
      case class Data(data:String,attr:List[String],v:Int)
      val c = Data("data",List("attr1","attr2"),10)
      val csv = Util.toCSV(c)
      csv should === ("data,attr1;attr2,10")
    }

    "produce CSV 'data,attr1,10' for case class with List(1)" in {
      case class Data(data:String,attr:List[String],v:Int)
      val c = Data("data",List("attr1"),10)
      val csv = Util.toCSV(c)
      csv should === ("data,attr1,10")
    }

    "produce CSV 'data,,10' for case class with List()" in {
      case class Data(data:String,attr:List[String],v:Int)
      val c = Data("data",List(),10)
      val csv = Util.toCSV(c)
      csv should === ("data,,10")
    }

    "produce CSV 'data,attr1;attr2,10' for case class with Seq(1,2)" in {
      case class Data(data:String,attr:Seq[String],v:Int)
      val c = Data("data",Seq("attr1","attr2"),10)
      val csv = Util.toCSV(c)
      csv should === ("data,attr1;attr2,10")
    }

    "produce CSV 'data,attr1,10' for case class with Seq(1)" in {
      case class Data(data:String,attr:Seq[String],v:Int)
      val c = Data("data",Seq("attr1"),10)
      val csv = Util.toCSV(c)
      csv should === ("data,attr1,10")
    }

    "produce CSV 'data,,10' for case class with Seq()" in {
      case class Data(data:String,attr:Seq[String],v:Int)
      val c = Data("data",Seq(),10)
      val csv = Util.toCSV(c)
      csv should === ("data,,10")
    }

    """replaceVar should replace '{"name" = "{name}"}'""" in {
      val e1 = """{"name" = "{name}"}"""
      val e2 = Util.replaceVar(e1,Map("name"->"User-1"))
      e2 should === ("""{"name" = "User-1"}""")
    }

    """replaceVar should replace multiple '{"name1" = "{name}","name2":"{name}"}'""" in {
      val e1 = """{"name1" = "{name}","name2":"{name}"}"""
      val e2 = Util.replaceVar(e1,Map("name"->"User-1"))
      e2 should === ("""{"name1" = "User-1","name2":"User-1"}""")
    }

    """replaceVar should replace different multiple '{"name1" = "{name}","name2":"{name}", "count1":{count},"count2":{count}}'""" in {
      val e1 = """{"name1" = "{name}","name2":"{name}", "count1":{count},"count2":{count}}"""
      val e2 = Util.replaceVar(e1,Map("name"->"User-1","count"->10))
      e2 should === ("""{"name1" = "User-1","name2":"User-1", "count1":10,"count2":10}""")
    }

    """replaceEnvVar should replace 'prefix_${USER}_suffix'""" in {
      val e1 = """prefix_${USER}_suffix'"""
      val e2 = Util.replaceEnvVar(e1,Map("USER" -> "1234"))
      e2 should === ("""prefix_1234_suffix'""")
    }

    """replaceEnvVar should replace 'prefix_{USER}_suffix'""" in {
      val e1 = """prefix_{USER}_suffix'"""
      val e2 = Util.replaceEnvVar(e1,Map("USER" -> "1234"))
      e2 should === ("""prefix_1234_suffix'""")
    }

    """replaceEnvVar should replace '{USER}'""" in {
      val e1 = """{USER}"""
      val e2 = Util.replaceEnvVar(e1,Map("USER" -> "1234"))
      e2 should === ("""1234""")
    }

    """replaceEnvVar should replace '${USER}'""" in {
      val e1 = """${USER}"""
      val e2 = Util.replaceEnvVar(e1,Map("USER" -> "1234"))
      e2 should === ("""1234""")
    }

    """replaceEnvVar should replace 'prefix_${NOT_FOUND}_suffix'""" in {
      val e1 = """prefix_${NOT_FOUND}_suffix'"""
      val e2 = Util.replaceEnvVar(e1)
      e2 should === ("""prefix__suffix'""")
    }

    """replaceEnvVar should NOT replace 'prefix_$NOT_FOUND_suffix' (not supported)""" in {
      val e1 = """prefix_$NOT_FOUND_suffix'"""
      val e2 = Util.replaceEnvVar(e1)
      e2 should !== ("""prefix__suffix'""")
    }

    """replaceEnvVar should replace all envs 'prefix_${USER}_fix_${USER}_suffix'""" in {
      val e1 = """prefix_${USER}_fix_${USER}_suffix'"""
      val e2 = Util.replaceEnvVar(e1,Map("USER" -> "1234"))
      e2 should === ("""prefix_1234_fix_1234_suffix'""")
    }

    "toBigInt convert '100000000000000000000000'" in {
      val v = Util.toBigInt("100000000000000000000000")
      v should === (BigInt("100000000000000000000000"))
    }

    "toBigInt convert '1.0E+20'" in {
      val v = Util.toBigInt("1.0E+20")
      v should === (BigInt("100000000000000000000"))
    }

    "toBigInt convert '0x9cbcf71f24d33e' to positive BigInt" in {
      val v = Util.toBigInt("0x9cbcf71f24d33e")
      v should === (BigInt("44117865932313406"))
    }

    "toJsonString should handle basic types" in {
      Util.toJsonString("text") should === (""""text"""")
      Util.toJsonString(123) should === ("123")
      Util.toJsonString(123L) should === ("123")
      Util.toJsonString(123.45) should === ("123.45")
      Util.toJsonString(true) should === ("true")
      Util.toJsonString(null) should === ("null")
    }

    "toJsonString should handle collections" in {
      Util.toJsonString(List(1, 2, 3)) should === ("[1,2,3]")
      Util.toJsonString(Array("a", "b")) should === ("""["a","b"]""")
      Util.toJsonString(Map("a" -> 1, "b" -> 2)) should === ("""{"a":1,"b":2}""")
    }

    "toJsonString should handle nested structures" in {
      val data = Map(
        "name" -> "test",
        "values" -> List(
          Map("x" -> 1, "y" -> 2),
          Map("x" -> 3, "y" -> 4)
        )
      )
      Util.toJsonString(data) should === ("""{"name":"test","values":[{"x":1,"y":2},{"x":3,"y":4}]}""")
    }

    "toJsonString should handle case classes" in {
      case class Person(name: String, age: Int)
      val person = Person("John", 30)
      Util.toJsonString(person) should === ("""{"name":"John","age":30}""")
    }

    "toJsonString should handle complex nested case classes" in {
      case class Address(street: String, city: String, zip: String)
      case class Contact(phone: String, email: String)
      case class Person(name: String, age: Int, address: Address, contacts: List[Contact])
      
      val data = List(
        Person(
          "John", 
          30,
          Address("123 Main St", "Boston", "02108"),
          List(
            Contact("555-0101", "john@email.com"),
            Contact("555-0102", "john.work@email.com")
          )
        ),
        Person(
          "Jane",
          28,
          Address("456 Oak Ave", "New York", "10001"),
          List(
            Contact("555-0201", "jane@email.com")
          )
        )
      )
      
      val expected = """[{"name":"John","age":30,"address":{"street":"123 Main St","city":"Boston","zip":"02108"},"contacts":[{"phone":"555-0101","email":"john@email.com"},{"phone":"555-0102","email":"john.work@email.com"}]},{"name":"Jane","age":28,"address":{"street":"456 Oak Ave","city":"New York","zip":"10001"},"contacts":[{"phone":"555-0201","email":"jane@email.com"}]}]"""
      
      Util.toJsonString(data) should === (expected)
    }
  }
}
