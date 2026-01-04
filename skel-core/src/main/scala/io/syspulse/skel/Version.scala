package io.syspulse.skel

import scala.jdk.CollectionConverters._
import io.syspulse.skel.util.Util

case class Version(major:Int,minor:Int,patch:Int,build:Option[String]=None,stage:Option[String]=None) extends Ordered[Version] {
  override def toString:String = build match {
    case Some(build) => s"$major.$minor.$patch-$build"
    case None => s"$major.$minor.$patch"
  }

  def toInt:Int = major * 1000000 + minor * 1000 + patch

  /**
   * Compare this version with another version.
   * Returns negative if this < other, zero if equal, positive if this > other.
   * Comparison is based on major, minor, and patch versions only.
   * Build and stage are not considered in comparison.
   */
  def compare(that: Version): Int = {
    val majorCmp = this.major.compareTo(that.major)
    if (majorCmp != 0) return majorCmp
    
    val minorCmp = this.minor.compareTo(that.minor)
    if (minorCmp != 0) return minorCmp
    
    this.patch.compareTo(that.patch)
  }
}

object Version {
  def apply(version:String):Version = {
    val parts = version.split("\\.").toList
    parts match {
      case major :: minor :: patch :: build :: stage :: Nil => Version(major.toInt,minor.toInt,patch.toInt,Some(build),Some(stage))
      case major :: minor :: patch :: build :: Nil => Version(major.toInt,minor.toInt,patch.toInt,Some(build),None)
      case major :: minor :: patch :: Nil => Version(major.toInt,minor.toInt,patch.toInt)
      case major :: minor :: Nil => Version(major.toInt,minor.toInt,0)
      case major :: Nil => Version(major.toInt,0,0)
      case _ => throw new IllegalArgumentException(s"Invalid version: $version")
    }
  }

  def apply(version:Int):Version = {
    Version(version / 1000000, (version % 1000000) / 1000, version % 1000)
  }

  /**
   * Find the maximum version from a sequence of versions.
   * Returns None if the sequence is empty.
   */
  def max(versions: Seq[Version]): Option[Version] = {
    if (versions.isEmpty) None
    else Some(versions.max)
  }

  /**
   * Parse version strings and find the latest (maximum) version.
   * Returns None if the sequence is empty or if all versions are invalid.
   */
  def latest(versions: Seq[String]): Option[Version] = {
    if (versions.isEmpty) return None
    
    val parsedVersions = versions.flatMap { v =>
      try {
        Some(Version(v))
      } catch {
        case _: Exception => None
      }
    }
    
    if (parsedVersions.isEmpty) None
    else Some(parsedVersions.max)
  }
}