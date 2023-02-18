package io.syspulse.skel.serde

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
//import io.syspulse.skel.util.Util

import scala.jdk.CollectionConverters

import com.sksamuel.avro4s._
import com.sksamuel.avro4s.AvroSchema

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.File

class AvroSpec extends AnyWordSpec with Matchers {
  
  import com.sksamuel.avro4s.{AvroOutputStream, AvroInputStream}
  
  val avroFile1 = "/tmp/file-1.avro"
  val avroFile2 = "/tmp/file-2.avro"

  "Avro" should {

    "serialize and deserialize String with file" in {
      val schema = AvroSchema[String]

      val os = AvroOutputStream.data[String].to(new File(avroFile1)).build()
      os.write("DataStr")
      os.flush()
      os.close()

      //info(s"${scala.io.Source.fromFile(avroFile1).getLines().mkString}")
      
      val is = AvroInputStream.data[String].from(new File(avroFile1)).build(schema)
      val o = is.iterator.take(1).toSeq.head
      is.close()

      o should === ("DataStr")
    }

    "serialize and deserialize String with stream" in {

      val schema = AvroSchema[String]

      val byteOut = new ByteArrayOutputStream()
      
      val os = AvroOutputStream.binary[String].to(byteOut).build()
      os.write("DataStr")
      os.flush()
      os.close()

      val byteIn = new ByteArrayInputStream(byteOut.toByteArray())

      val is = AvroInputStream.binary[String].from(byteIn).build(schema)
      val o = is.iterator.take(1).toSeq.head
      is.close()

      o should === ("DataStr")
    }

    "serialize and deserialize DataObj with File" in {
      val ts = ZonedDateTime.now()

      val schema = AvroSchema[DataAvroObj]

      val os = AvroOutputStream.data[DataAvroObj].to(new File(avroFile2)).build()

      os.write(DataAvroObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes))
      os.flush()
      os.close()

      val is = AvroInputStream.data[DataAvroObj].from(new File(avroFile2)).build(schema)
      val o = is.iterator.take(1).toSeq.head
      is.close()

      //o should === (DataAvroObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes))      
      o.id should === (UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"))
      o.ts should === (ts.toString)
      o.str should === ("str")
      o.int should === (10)
      o.long should === (Long.MaxValue)
      o.data should === ("data".getBytes())
    }

    "serialize and deserialize DataObj with AvroSerde" in {
      val ts = ZonedDateTime.now()

      val bb = AvroSerde.serialize(
        DataAvroObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes)
      )
      
      val o = AvroSerde.deserialize[DataAvroObj](bb)
      
      //o === (DataAvroObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes))
      o.id should === (UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"))
      o.ts should === (ts.toString)
      o.str should === ("str")
      o.int should === (10)
      o.long should === (Long.MaxValue)
      o.data should === ("data".getBytes())
    }
  }
}
