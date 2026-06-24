package io.syspulse.skel.dns

import scala.util.{Try, Success}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.Locale

// --- .fi -----------------------------------------------------------------------
/*                                                                   
domain.............: lido.fi
status.............: Registered
created............: 5.10.2020 22:24:42
expires............: 5.10.2026 22:24:42
available..........: 5.11.2026 22:24:42
modified...........: 23.9.2025 08:04:31
RegistryLock.......: no

Nameservers

nserver............: nancy.ns.cloudflare.com [OK]
nserver............: jaime.ns.cloudflare.com [OK]

DNSSEC

dnssec.............: no

Holder

holder.............: Private person

Registrar

registrar..........: 1API GmbH
www................: https://www.1api.net

>>> Last update of WHOIS database: 24.6.2026 9:02:05 (EET) <<<

Copyright (c) Finnish Transport and Communications Agency Traficom
*/

class FiResolver extends WhoisResolver {

  private val tsFormatFi = DateTimeFormatter.ofPattern("d.M.yyyy H:mm:ss", Locale.ROOT)
  private val tsFormatFiDate = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT)

  def parseFiDate(date: String): Long = {
    val s = date.trim
    Try(LocalDateTime.parse(s, tsFormatFi))
      .orElse(Try(LocalDate.parse(s, tsFormatFiDate).atStartOfDay()))
      .get
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli
  }

  override def parseResponse(domain: String, body: String): Try[DnsInfo] = {
    val fields = body.split("\n").flatMap { line =>
      line.split(":", 2).toList match {
        case key :: value :: Nil =>
          key.replaceAll("\\.+$", "").trim.toLowerCase match {
            case k @ ("created" | "expires" | "modified") =>
              Some((k, value.trim))
            case "nserver" =>
              value.trim.split("\\s+").headOption.filter(_.nonEmpty).map(n => ("nserver", n))
            case _ => None
          }
        case _ => None
      }
    }

    def field(field: String): Option[Long] =
      fields.find(_._1 == field).map(_._2).map(parseFiDate)

    Success(DnsInfo(
      domain = domain,
      created = field("created"),
      updated = field("modified"),
      expire = field("expires"),
      ip = "",
      ns = fields.collect { case ("nserver", n) => n }.toSeq,
    ))
  }
}
