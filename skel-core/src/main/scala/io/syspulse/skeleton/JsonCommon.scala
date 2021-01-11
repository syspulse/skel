package io.syspulse.skeleton


import spray.json.DefaultJsonProtocol

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import io.jvm.uuid.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

class JsonCommon  {
  
  import DefaultJsonProtocol._
  
  val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

  implicit object DateTimeFormat extends JsonFormat[LocalDateTime] {
    override def write(obj: LocalDateTime) : JsValue = JsString(fmt.format(obj))

    override def read(json: JsValue) : LocalDateTime = json match {
      case JsString(rawDate) => {
        try {
          LocalDateTime.parse(rawDate)
        } catch {
          case iae: IllegalArgumentException => deserializationError("Invalid date format")
          case _: Exception => None
        }
      }
      match {
        case d: LocalDateTime => d
        case None => deserializationError(s"Couldn't parse DateTime, got $rawDate")
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

}
