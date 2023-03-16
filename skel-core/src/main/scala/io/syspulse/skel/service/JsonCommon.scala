package io.syspulse.skel.service

import spray.json.DefaultJsonProtocol

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import io.jvm.uuid.UUID
import java.time.{LocalDateTime,ZonedDateTime}
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}
import spray.json.JsNumber
import spray.json.JsTrue
import spray.json.JsFalse
import scala.collection.SortedSet
import spray.json.JsArray
import spray.json.RootJsonFormat
import spray.json.CollectionFormats
import spray.json.JsNull
import spray.json.JsObject

object JsonCommon extends DefaultJsonProtocol with CollectionFormats {
  implicit def sortedSetFormat[T :JsonFormat](implicit ordering: Ordering[T]) = viaSeq[SortedSet[T], T](seq => SortedSet(seq :_*))
}

trait JsonCommon extends DefaultJsonProtocol with CollectionFormats { 
  //import DefaultJsonProtocol._

  // implicit def sortedSetFormat[T :JsonFormat] = new RootJsonFormat[SortedSet[T]] {
  //   def write(list: SortedSet[T]) = JsArray(list.map(_.toJson).toVector)
  //   def read(value: JsValue): SortedSet[T] = value match {
  //     case JsArray(elements) => SortedSet[T](elements.toIterator.map(_.convertTo[T]).toSet)
  //     case x => deserializationError("Expected List as JsArray, but got " + x)
  //   }
  // }

  //val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
  val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

  implicit object LocalDateTimeFormat extends JsonFormat[LocalDateTime] {
    override def write(obj: LocalDateTime) : JsValue = JsString(dateFormatter.format(obj))

    override def read(json: JsValue) : LocalDateTime = json match {
      case JsString(rawDate) => {
        try {
          LocalDateTime.parse(rawDate)
        } catch {
          case iae: IllegalArgumentException => deserializationError(s"Invalid date format: '${rawDate}'")
          case _: Exception => None
        }
      }
      match {
        case d: LocalDateTime => d
        case None => deserializationError(s"Couldn't parse LocalDateTime: '$rawDate'")
      }

    }
  }

  implicit object ZonedDateTimeFormat extends JsonFormat[ZonedDateTime] {
    override def write(obj: ZonedDateTime) : JsValue = JsString(dateFormatter.format(obj))

    override def read(json: JsValue) : ZonedDateTime = json match {
      case JsString(rawDate) => {
        try {
          ZonedDateTime.parse(rawDate)
        } catch {
          case iae: IllegalArgumentException => deserializationError(s"Invalid date format: '${rawDate}'")
          case _: Exception => None
        }
      }
      match {
        case d: ZonedDateTime => d
        case None => deserializationError(s"Couldn't parse ZonedDateTime: '$rawDate'")
      }

    }
  }

  def parseIsoDateString(date: String): Option[Date] = {
    if (date.length != 28) None
    else try Some(localIsoDateFormatter.get().parse(date))
    catch {
      case p: ParseException => None
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }

  def dateToIsoString(date: Date) = localIsoDateFormatter.get().format(date)

  implicit object DateFormat extends JsonFormat[Date] {

    def write(date : Date) : JsValue = JsString(dateToIsoString(date))

    def read(json: JsValue) : Date = json match {

      case JsString(rawDate) => parseIsoDateString(rawDate) match {
        case None => deserializationError(s"Expected ISO Date format, got $rawDate")
        case Some(isoDate) => isoDate
      }

      case unknown => deserializationError(s"Expected JsString, got $unknown")
    }
  }

  // attention - does not support arrays 
  implicit object AnyFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case n: Long => JsNumber(n)
      case n: BigInt => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
      case d: Double => JsNumber(d)
    }
    def read(value: JsValue) = value match {
      case JsNumber(n) => n
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
      case JsNull => None
      case JsArray(elements) => elements.map(e => read(e)).toArray
      case JsObject(fields) => fields.map{ case(k,v) => k -> read(v)}.toMap
    }
  }

}
