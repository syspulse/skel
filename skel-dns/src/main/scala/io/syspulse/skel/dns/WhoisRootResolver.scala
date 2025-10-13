package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.LocalDate

class WhoisRootResolver extends WhoisResolver() {
  override def nsName:String = "nserver:"
  override def createdName:String = "created:"
  override def updatedName:String = "changed:"  
  
  override val tsFormatISO = Seq(
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),    
  )  

  override def parseDate(date:String) = {
    tsFormatISO.map(f => Try(LocalDate.parse(date,f)))
      .find(_.isSuccess)
      .map(_.get)
      .get
      .atStartOfDay()  // Convert LocalDate to LocalDateTime
      .toInstant(java.time.ZoneOffset.UTC)  // Convert to Instant with UTC timezone
      .toEpochMilli
  }
}
