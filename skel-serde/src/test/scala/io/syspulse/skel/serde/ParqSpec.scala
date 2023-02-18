package io.syspulse.skel.serde

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

import scala.jdk.CollectionConverters

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.File

import com.github.mjakubowski84.parquet4s.{ParquetReader, ParquetWriter, Path}
import java.nio.file.Files
import scala.util.Random

import io.syspulse.skel.serde.Parq._

class ParqSpec extends AnyWordSpec with Matchers {
  
  //val tmp  = Path(Files.createTempDirectory("/tmp/skel"))
  val file1 = "/tmp/file-1.parquet"
  val file2 = "/tmp/file-2.parquet"
  
  
  case class Data(id: Int, text: String)

  "Parquet" should {

    "serialize and deserialize Data(Int,String)" in {            
      os.remove(os.Path(file1))

      val count = 1
      val data1  = (1 to count).map(i => Data(id = i, text = Random.nextString(4)))
      
      ParquetWriter.of[Data].writeAndClose(Path(file1), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      //info(s"${Util.hex(bin1.getBytes())}")

      val data2 = ParquetReader.as[Data].read(Path(file1))
      
      // try data2.foreach(println)
      // finally data2.close()

      data2.toList should === (data1.toList)
      data2.close()
    }

    "serialize and deserialize DataObj(UUID,ZoneDateTime,String,Long,Array)" in {            
      os.remove(os.Path(file2))

      val ts = ZonedDateTime.now()
      val data1 = Seq(
        DataObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts,"str",10,Long.MaxValue,"data".getBytes)
      )
      
      ParquetWriter.of[DataObj].writeAndClose(Path(file2), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      //info(s"${Util.hex(bin1.getBytes())}")

      val data2 = ParquetReader.as[DataObj].read(Path(file2))      
      // try data2.foreach(println)
      // finally data2.close()
      //data2.toList should === (data1.toList)

      val o = data2.head
      o.id should === (UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"))
      o.ts.format(Util.tsFormatSerde) should === (ts.format(Util.tsFormatSerde))
      o.str should === ("str")
      o.int should === (10)
      o.long should === (Long.MaxValue)
      o.data should === ("data".getBytes())

      data2.close()
    }

    
  }
}
