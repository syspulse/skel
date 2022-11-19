package io.syspulse.skel.video.tms

import scala.jdk.CollectionConverters._

import scala.util.Random

object TmsParser {
  
  def fromFile(file:String) = {
    fromIterator(scala.io.Source.fromFile(file).getLines())
  }

  def fromIterator(xml:Iterator[String]):Iterator[Tms] = {
    xml
      .map(_.trim)
      .filter(s => 
        s.startsWith("<title>") || 
        s.startsWith("<tmsProgramId>") || 
        s.startsWith("<category>"))
      .map(s => s.split("[<>]")(2))
      .grouped(3)
      .map(a => Tms(a(2),a(1),a(0)))
  }

  def fromSeq(xml:Seq[String]) = {
    fromIterator(xml.iterator)
  }

  def fromString(xml:String) = {
    fromIterator(xml.split("\\n").iterator)
  }
}