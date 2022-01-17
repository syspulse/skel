package io.syspulse.skel.serde

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.sksamuel.avro4s._
import com.sksamuel.avro4s.AvroOutputStream

object AvroSerde {
  def serialize[T: Encoder : SchemaFor](t: T): Array[Byte] = {
    val os = new ByteArrayOutputStream
    val avro = AvroOutputStream.data[T].to(os).build()
    avro.write(t)
    avro.close()
    os.toByteArray()
  }

  def deserialize[T: SchemaFor : Decoder](bytes: Array[Byte]): T = {
    AvroInputStream.data.from(bytes).build(implicitly[SchemaFor[T]].schema).iterator.next()
  }
}

