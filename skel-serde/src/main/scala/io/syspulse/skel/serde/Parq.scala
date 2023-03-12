package io.syspulse.skel.serde

import java.io._
import java.time.ZonedDateTime
import io.jvm.uuid._

import com.github.mjakubowski84.parquet4s._

import org.apache.parquet.schema._
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY

import io.syspulse.skel.util.Util

object Parq { 
  import ValueCodecConfiguration._

  // ----- UUID ------------------------------------------------------------------------------------------------
  implicit val uuidTypeCodec: OptionalValueCodec[UUID] = new OptionalValueCodec[UUID] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): UUID =
      value match {
          case BinaryValue(binary) => UUID.fromByteArray(binary.getBytes(),0)
        }
    override protected def encodeNonNull(data: UUID, configuration: ValueCodecConfiguration): Value =
      BinaryValue(data.byteArray)
  }

  implicit val uuidSchema: TypedSchemaDef[UUID] = SchemaDef
      .primitive(
        //primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        primitiveType         = PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY,
        length                = Some(16),
        //logicalTypeAnnotation = Option(LogicalTypeAnnotation.stringType())
        logicalTypeAnnotation = Option(LogicalTypeAnnotation.uuidType())
      )
      .typed[UUID]

  // ----- ZoneDateTime -------------------------------------------------------------------------------------------
  implicit val zonedDateTypeCodec: OptionalValueCodec[ZonedDateTime] = new OptionalValueCodec[ZonedDateTime] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): ZonedDateTime =
      value match {
          case BinaryValue(binary) => Util.toZoneDateTime(binary.toStringUsingUTF8,Util.tsFormatSerde)
        }
    override protected def encodeNonNull(data: ZonedDateTime, configuration: ValueCodecConfiguration): Value =
      BinaryValue(data.format(Util.tsFormatSerde).getBytes())
  }

  implicit val zonedDataTimeSchema: TypedSchemaDef[ZonedDateTime] = SchemaDef
      .primitive(
        primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        logicalTypeAnnotation = Option(LogicalTypeAnnotation.stringType())
      )
      .typed[ZonedDateTime]

  // ----- BigInt ------------------------------------------------------------------------------------------------
  implicit val bigIntTypeCodec: OptionalValueCodec[BigInt] = new OptionalValueCodec[BigInt] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): BigInt =
      value match {
          //case BinaryValue(binary) => BigInt(binary.getBytes())
          case BinaryValue(binary) => Decimals.decimalFromBinary(binary,18).toBigInt
        }
    override protected def encodeNonNull(data: BigInt, configuration: ValueCodecConfiguration): Value =
      //BinaryValue(data.toByteArray)
      BinaryValue(Decimals.binaryFromDecimal(BigDecimal(data)))
  }

  implicit val bigIntSchema: TypedSchemaDef[BigInt] = SchemaDef
      .primitive(
        //primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,        
        primitiveType         = PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY,
        length                = Some(16),
        logicalTypeAnnotation = Option(LogicalTypeAnnotation.decimalType(18,38))
      )
      .typed[BigInt]
}

// ----- Any as String ----------------------------------------------------------------------------------------------
object ParqAnyString { 
  import ValueCodecConfiguration._

  // Treat is as String
  implicit val anyTypeCodec: OptionalValueCodec[Any] = new OptionalValueCodec[Any] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): Any =
      value match {
          case BinaryValue(binary) => new String(binary.getBytes())
        }
    override protected def encodeNonNull(data: Any, configuration: ValueCodecConfiguration): Value =
      BinaryValue(data.toString.getBytes())
  }

  implicit val anySchema: TypedSchemaDef[Any] = SchemaDef
      .primitive(
        primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        logicalTypeAnnotation = Option(LogicalTypeAnnotation.stringType())        
      )
      .typed[Any]
}

// ----- Any as Serializable ------------------------------------------------------------------------------------------
object ParqAnySerializable { 
  import ValueCodecConfiguration._
  implicit val abstactClassTypeCodec: OptionalValueCodec[Any] = new OptionalValueCodec[Any] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): Any =
      value match {
          case BinaryValue(binary) => Serde.deserialize(binary.getBytes())
      }

    override protected def encodeNonNull(data: Any, configuration: ValueCodecConfiguration): Value = {
      // Attention: Enforced cast
      // Exception is good since it always requires class to be serializable
      BinaryValue(Serde.serialize(data.asInstanceOf[Serializable]))
    }
  }

  implicit val abstractClassSchema: TypedSchemaDef[Any] = SchemaDef
      .primitive(
        primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        logicalTypeAnnotation = None
      )
      .typed[Any]
}

// ----- Any Type as Serializable ------------------------------------------------------------------------------------------
// This is useful for Abstract Types 
object ParqCodecTypedSerializable { 
  import ValueCodecConfiguration._

  class AbstractClassCodec[T <: Serializable] extends OptionalValueCodec[T] {
    override protected def decodeNonNull(value: Value, configuration: ValueCodecConfiguration): T =
      value match {
          case BinaryValue(binary) => Serde.deserialize(binary.getBytes())
      }

    override protected def encodeNonNull(data: T, configuration: ValueCodecConfiguration): Value = {
      BinaryValue(Serde.serialize(data))
    }
    
    implicit val abstractClassSchema: TypedSchemaDef[T] = SchemaDef
      .primitive(
        primitiveType         = PrimitiveType.PrimitiveTypeName.BINARY,
        logicalTypeAnnotation = None
      )
      .typed[T]
  }

  def forClass[T <: Serializable] = {
    new AbstractClassCodec[T]()
  }
}
