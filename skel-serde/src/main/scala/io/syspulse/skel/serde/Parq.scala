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