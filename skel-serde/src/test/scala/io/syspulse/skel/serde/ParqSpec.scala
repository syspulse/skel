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
import org.apache.parquet.hadoop.ParquetFileWriter.Mode
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.hadoop.{ParquetWriter => HadoopParquetWriter}
import org.apache.hadoop.conf.Configuration

import com.github.mjakubowski84.parquet4s.{ParquetReader, ParquetWriter, Path}
import java.nio.file.Files
import scala.util.Random

import io.syspulse.skel.serde.Parq._

// Attention: Abstract classes are not supported, so can only be encapsulated as ByteArray
abstract class DataAbstract extends Serializable
case class DataInsideId(id:String) extends DataAbstract

case class DataAny(data:Any)
case class DataWithAbstract(id: Int, text: String,b:Byte, inside:DataAbstract)
case class DataBig(v:BigInt)

import com.github.mjakubowski84.parquet4s._
import org.apache.parquet.schema._
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY

object ParqCodecAbstractClass { 
  import ValueCodecConfiguration._
  implicit val abstactClassTypeCodec: OptionalValueCodec[DataAbstract] = new OptionalValueCodec[DataAbstract] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): DataAbstract =
      value match {
          case BinaryValue(binary) => Serde.deserialize(binary.getBytes())
      }

    override protected def encodeNonNull(data: DataAbstract, configuration: ValueCodecConfiguration): Value = {
      BinaryValue(Serde.serialize(data))
    }
  }

  implicit val abstractClassSchema: TypedSchemaDef[DataAbstract] = SchemaDef
      .primitive(
        primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        logicalTypeAnnotation = None
      )
      .typed[DataAbstract]
}

class ParqSpec extends AnyWordSpec with Matchers {
  
  //val tmp  = Path(Files.createTempDirectory("/tmp/skel"))
  val file1 = "/tmp/skel-seder/parq/file-1.parquet"
  val file2 = "/tmp/skel-seder/parq/file-2.parquet"
  
  import ParqCodecAbstractClass._

  "Parquet" should {

    "serialize and deserialize Any type in Data(Any) as String" in {
      import ParqAnyString._

      os.remove(os.Path(file1))

      val count = 1
      val data1  = (1 to count).map(i => DataAny(data=s"data-${Random.nextLong()}"))
      
      ParquetWriter.of[DataAny].writeAndClose(Path(file1), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      
      val data2 = ParquetReader.as[DataAny].read(Path(file1))
            
      data2.toList should === (data1.toList)
      data2.close()
    }

    "serialize and deserialize Any type in Data(Any) as Serializable" in {
      import ParqAnySerializable._

      os.remove(os.Path(file1))

      val count = 1
      val data1  = (1 to count).map(i => DataAny(data=s"data-${Random.nextLong()}"))
      
      ParquetWriter.of[DataAny].writeAndClose(Path(file1), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      
      val data2 = ParquetReader.as[DataAny].read(Path(file1))
            
      data2.toList should === (data1.toList)
      data2.close()
    }

    "serialize and deserialize Data(Int,String,Byte,Abstract)" in {            
      os.remove(os.Path(file1))

      val count = 1
      val data1  = (1 to count).map(i => DataWithAbstract(id = i, text = Random.nextString(4), b = Random.nextBytes(1).head, inside = DataInsideId(s"{id}")))
      
      ParquetWriter.of[DataWithAbstract].writeAndClose(Path(file1), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      //info(s"${Util.hex(bin1.getBytes())}")

      val data2 = ParquetReader.as[DataWithAbstract].read(Path(file1))
      
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

    "serialize and deserialize BigInt" in {            
      os.remove(os.Path(file1))

      val data1  = Seq(
        DataBig(BigInt(9001))
      )
      
      ParquetWriter.of[DataBig].writeAndClose(Path(file1), data1)

      val bin1 = os.read(os.Path(file1))
      bin1.size !== (0)
      //info(s"${Util.hex(bin1.getBytes())}")

      val data2 = ParquetReader.as[DataBig].read(Path(file1))
            
      data2.toList should === (data1.toList)
      data2.close()
    }    

    "write as stream to separate files" in {
      val dir = "/tmp/skel-seder/parq/stream"
      os.remove.all(os.Path(s"${dir}",os.pwd))
      os.makeDir.all(os.Path(dir,os.pwd))

      val data1  = (1 to 100).map(i => DataWithAbstract(id = i, text = Random.nextString(4),i.toByte,DataInsideId(s"${i}")))

      val writeOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.UNCOMPRESSED,
        //hadoopConf = conf // optional hadoopConf
      )
            
      data1.grouped(10).foreach{ g => {
        val file1 = s"${dir}/file-${System.currentTimeMillis}.parq"
        val pw = ParquetWriter.of[DataWithAbstract].build(Path(file1))
        g.foreach{ d => 
          pw.write(Seq(d))
        }
        pw.close()
      }}
      
      info(s"files=${os.list(os.Path(dir,os.pwd)).toList}")
    }

    "compare snappy to csv sizes" ignore {
      val dir = "/tmp/skel-seder/parq/size"
      os.remove.all(os.Path(s"${dir}",os.pwd))
      os.makeDir.all(os.Path(dir,os.pwd))

      case class Tx(ts:Long, hash:String,from:String,to: String, value:BigInt,block:Long, nonce:Long)

      val from = Util.hex(Random.nextBytes(32))
      val data  = (1 to 10000).map(i => 
        Tx(
          System.currentTimeMillis - i * 1000 * 30,
          hash = Util.hex(Random.nextBytes(32)),
          from = from, //Util.hex(Random.nextBytes(32)),
          to = Util.hex(Random.nextBytes(32)),
          value = BigInt(Random.nextLong()),
          block = 60000 + i ,
          nonce = i
        )
      )

      val snappyOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.SNAPPY,        
      )

      val gzipOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.GZIP,
      )

      val lzoOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.LZO,
      )

      val brotliOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.BROTLI,
      )

      // set this in sbt 
      // eval System.setProperty("java.library.path", "hadoop-3.2.2/lib/native"
      val lz4Options = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.LZ4,
      )
      val zstdOptions = ParquetWriter.Options(
        writeMode = Mode.OVERWRITE,
        compressionCodecName = CompressionCodecName.ZSTD,
      )

      Seq(snappyOptions, gzipOptions, lz4Options, zstdOptions).foreach{ opts => 
        data.grouped(data.size).foreach{ g => {
          val file1 = s"${dir}/file-${System.currentTimeMillis}.parq.${opts.compressionCodecName.toString.toLowerCase()}"
          val pw = ParquetWriter.of[Tx].options(opts).build(Path(file1))
          g.foreach{ d => 
            pw.write(Seq(d))
          }
          pw.close()
        }}
      }      

      os.write.over(os.Path(s"${dir}/file.csv",os.pwd),
        data.map( e => Util.toCSV(e)).mkString("\n")
      )
      
      val ss = os.list(os.Path(dir,os.pwd)).map(f => s"${f}: ${new java.io.File(f.toString).length}\n")
      info(s"files=${ss}")
    }
  }
}
