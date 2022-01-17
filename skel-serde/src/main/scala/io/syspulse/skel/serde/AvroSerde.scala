package io.syspulse.skel.serde

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.sksamuel.avro4s._
import com.sksamuel.avro4s.AvroOutputStream

class AvroSerde[T] {
  // def serialize(t: T, schema: AvroSchema[T]): Array[Byte] = {
    
  //   val os = AvroOutputStream.data[T]
  //   os.write(Seq(pepperoni, hawaiian))
  //   os.flush()
  //   os.close()
  //  }

  //  def deserializeSingleBinary(a: Array[Byte]): T = {
  //     val ais = AvroInputStream.binary(new ByteArrayInputStream(a))
  //     try ais.iterator.next finally ais.close()
  //  }
}

