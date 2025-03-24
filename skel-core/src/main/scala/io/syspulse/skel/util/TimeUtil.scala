package io.syspulse.skel.util

import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.{Try,Success,Failure}
import scala.util.Random

import java.util.concurrent.TimeUnit
import java.util.Locale

import java.nio.charset.StandardCharsets

import java.time.Duration
import java.time._
import java.time.format._
import java.time.temporal._
import java.time.{ZoneId,ZonedDateTime,LocalDateTime,Instant}
import java.time.format._

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

object TimeUtil {
  val formatsZone = Seq(
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"),    
  )

  val formatsLocal = Seq(
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("MM/dd/yyyy")
  )

  val wordsMap = Map(
    "one" -> 1L,
    "two" -> 2L,
    "three" -> 3L,
    "four" -> 4L,
    "five" -> 5L,
    "six" -> 6L,
    "seven" -> 7L,
    "eight" -> 8L,
    "nine" -> 9L,
    "ten" -> 10L,
  )

  def wordToLong(word:String):Long = {
    val w = word.trim
    if(w.size == 0) return 0L
    if(w.charAt(0).isDigit) w.toLong else wordsMap.get(w.toLowerCase).getOrElse(0L)
  }

  def wordToTs(word:String,default:Long = 0L):Try[Long] = wordToDate(word,default = default).map(_.toInstant().toEpochMilli())
  
  // guess date from human 
  def wordToDate(word:String,tHour:Int = 0,tMin:Int=0,tSec:Int=0,default:Long = 0L):Try[ZonedDateTime] = {
    val pattern = word.trim.toLowerCase.replaceAll("\\s+","")
    pattern match {
      case "" => Success(ZonedDateTime.ofInstant(Instant.ofEpochMilli(default),ZoneId.systemDefault()))
      case "now" => Success(ZonedDateTime.now)
      case "today" => Success(ZonedDateTime.now.withHour(tHour).withMinute(tMin).withSecond(tSec))
      case "yesterday" => Success(ZonedDateTime.now.minusDays(1).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case "lastweek" | "week" => Success(ZonedDateTime.now.minusWeeks(1).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case "lastmonth" | "month" => Success(ZonedDateTime.now.minusMonths(1).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case "lastyear" | "year" => Success(ZonedDateTime.now.minusYears(1).withHour(tHour).withMinute(tMin).withSecond(tSec))
      
      case s"${x}daysago" => Success(ZonedDateTime.now.minusDays(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}dayago" => Success(ZonedDateTime.now.minusDays(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}weeksago" => Success(ZonedDateTime.now.minusWeeks(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}weekago" => Success(ZonedDateTime.now.minusWeeks(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      
      case s"${x}monthago" => Success(ZonedDateTime.now.minusMonths(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}monthsago" => Success(ZonedDateTime.now.minusMonths(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}yearago" => Success(ZonedDateTime.now.minusYears(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${x}yearsago" => Success(ZonedDateTime.now.minusYears(wordToLong(x)).withHour(tHour).withMinute(tMin).withSecond(tSec))

      case s"${d}.${m}.${y}" => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withYear(y.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${d}/${m}/${y}" => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withYear(y.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${d}.${m}" => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${d}/${m}" => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      
      case s"${d}-${m}-${y}" if d.size < 3 && pattern.size <= "dd-MM-yyyy".size  => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withYear(y.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      case s"${y}-${m}-${d}" if y.size == 4 && pattern.size <= "yyyy-MM-dd".size => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withYear(y.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      
      case s"${d}-${m}" if pattern.size <= "dd-MM".size => Success(ZonedDateTime.now.withDayOfMonth(d.toInt).withMonth(m.toInt).withHour(tHour).withMinute(tMin).withSecond(tSec))
      
      case s => // try to parse anything
        // try timestamp first
        if(s.head.isDigit && s.size == System.currentTimeMillis.toString.size) {
          return Success(ZonedDateTime.ofInstant(Instant.ofEpochMilli(s.toLong), ZoneId.systemDefault()))
        } else {
          formatsZone.foreach{ f => 
            try {
              return Success(ZonedDateTime.parse(word, f))
            } catch {
              case e:Exception => // just repeat
            }
          }

          formatsLocal.foreach{ f => 
            try {
              val ldt = LocalDateTime.parse(word, f)
              return Success(ZonedDateTime.of(ldt,ZoneId.systemDefault()))
            } catch {
              case e:Exception => // just repeat
            }
          }

          // try timestamp
          return Success(ZonedDateTime.ofInstant(Instant.ofEpochMilli(s.toLong), ZoneId.systemDefault()))
        }
        Failure(new Exception(s"could not parse: ${word}"))
    }
  }

  def wordToDateRange(word:String):(Try[ZonedDateTime],Try[ZonedDateTime]) = {
    if(!word.contains("-")) 
      return (wordToDate(word),Success(ZonedDateTime.now()))

    word.split("-") match {
      case Array(ts0,ts1) => (wordToDate(ts0),wordToDate(ts1,23,59,59))
      case Array(ts0,ts1,_) => (wordToDate(ts0),wordToDate(ts1,23,59,59))
    }
  }    

  def wordToTsRage(word:String):(Try[Long],Try[Long]) = {
    val (d0,d1) = wordToDateRange(word)
    (d0.map(_.toInstant.toEpochMilli),d1.map(_.toInstant.toEpochMilli))
  }

  def now:Long = ZonedDateTime.now.toInstant.toEpochMilli

  def duration(tsPast:Long,tsNow:Long = Instant.now.toEpochMilli) = {
    val past = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsPast), ZoneId.systemDefault)
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsNow), ZoneId.systemDefault)
    Duration.between(past, now).toSeconds()
  }

  def durationToHuman(sec:Double): String = {
    import scala.math._
    sec match {
      case ss if ss < 1.0 => "< 1 second"
      case mm if (mm / 60.0) < 1.0 => s"${(mm/(1.0)).toLong} sec"
      case hh if (hh / (60.0*60.0)) < 1.0 => {val m = hh/(60.0); s"${m.toLong} min " + durationToHuman((m-floor(m))* 60.0)}
      case dd if (dd / (60.0*60.0*24.0)) < 1.0 => {val h = dd/(60.0*60.0); s"${h.toLong} hours " + durationToHuman((h-floor(h))* (60.0*60.0))}
      case m if (m / (60.0*60.0*24.0*30.0)) < 1.0 => {val d = m/(60.0*60.0*24.0); s"${d.toLong} days " + durationToHuman((d-floor(d))* (60.0*60.0*24.0))}
      case y if (y / (60.0*60.0*24.0*30.0*12.0)) < 1.0 => {val m = y/(60.0*60.0*24.0*30.0); s"${m.toLong} months "+durationToHuman((m-floor(m))* (60.0*60.0*24.0*30.0))}
      case _ => { val y=sec / (60.0*60.0*24.0*30.0*12.0); s"${new java.math.BigDecimal(floor(y)).toPlainString} years " + durationToHuman((y-floor(y))* (60.0*60.0*24.0*30.0*12.0))}
    }
  }

  // embedded quottes: "text with blanks" 
  def parseText(txt:String):Array[String] = {
    var q = -1; 
    txt.split("""((?=")|(?<="))""")
      .zipWithIndex
      .flatMap{ case(s,i) => { 
        if(s!="\"") {
          if(q==i) Array(s) else s.split("\\s+") 
        } else { 
          if(q == -1) q=i+1 else q= -1; 
          Array[String]()
        }
      }}
      .filter(_.size > 0)
  }

  def humanToMillis(freq: String): Long = {
    val pattern = """(\d+)\s*(ms|msec|millisecond|sec|second|min|minute|hour|day)s?""".r
    freq.toLowerCase match {
      case pattern(value, unit) => 
        val milliseconds = unit match {
          case "ms" | "msec" | "millisecond" | "milliseconds" => 1L
          case "sec" | "second" | "seconds" => 1000L
          case "min" | "minute" | "minutes" => 60000L
          case "hour" | "hours" => 3600000L
          case "day" | "days" => 86400000L
        }
        value.toLong * milliseconds
      case _ => 
        freq.toLong
    }
  }
}

