package io.syspulse.skel.geo
/**
  * Original code: https://github.com/davidallsopp/geohash-scala
  * All Credits to https://github.com/davidallsopp
*/


/**
 * Note that intercalate and extracalate are only strict inverses if the input lists to intercalate
 * are either of equal lengths, or the first list is one element longer than the second list.
 */
object Calate {

  /**
   * Interlace two lists.
   *
   * E.g. intercalate(List(1,3,5), List(2,4)) == List(1,2,3,4,5)
   *
   * "Extra" numbers, if the lists have unequal lengths, will be included on the tail of the output list.
   */
  def intercalate[A](a: List[A], b: List[A]): List[A] = a match {
    case h :: t => h :: intercalate(b, t)
    case _      => b
  }

  /**
   * De-interlace two lists.
   *
   * E.g. extracalate(List(1,2,3,4,5)) == (List(1,3,5), List(2,4))
   */
  def extracalate[A](a: Seq[A]): (List[A], List[A]) =
    a.foldRight((List[A](), List[A]())) { case (b, (a1, a2)) => (b :: a2, a1) }

}

/**
 * A specialised implementation for geohash; e.g. does not check that size of input list of bits is 5.
 *
 * NB At present, only handles lowercase input
 */
object Base32 {
  val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
  val BITS = Array(16, 8, 4, 2, 1) // scalastyle:ignore
  val TODEC = Map(BASE32.zipWithIndex: _*)

  /** Convert list of boolean bits to a base-32 character. Only the first 5 bits are considered.*/
  def toBase32(bin: Seq[Boolean]): Char = BASE32((BITS zip bin).collect { case (x, true) => x }.sum)

  private def intToBits(i: Int) = (4 to 0 by -1) map (x => (i >> x & 1) == 1)

  def isValid(s: String): Boolean = !s.isEmpty() && s.forall(TODEC.contains(_))

  /** Convert a base-32 string to a list of bits (booleans) */
  def toBits(s: String): Seq[Boolean] = (s.flatMap(TODEC.andThen(b => intToBits(b))))
}


/** GeoHash encoding/decoding as per http://en.wikipedia.org/wiki/Geohash */
object Geohash {

  val LAT_RANGE = (-90.0, 90.0)
  val LON_RANGE = (-180.0, 180.0)

  // Aliases, utility functions
  type Bounds = (Double, Double)
  private def mid(b: Bounds) = (b._1 + b._2) / 2.0
  implicit class BoundedNum(x: Double) { def in(b: Bounds): Boolean = x >= b._1 && x <= b._2 }

  /**
   * Encode lat/long as a base32 geohash.
   *
   * Precision (optional) is the number of base32 chars desired; default is 12, which gives precision well under a meter.
   */
  def encode(lat: Double, lon: Double, precision: Int=12): String = { // scalastyle:ignore
    require(lat in LAT_RANGE, "Latitude out of range")
    require(lon in LON_RANGE, "Longitude out of range")
    require(precision > 0, "Precision must be a positive integer")
    val rem = precision % 2 // if precision is odd, we need an extra bit so the total bits divide by 5
    val numbits = (precision * 5) / 2
    val latBits = findBits(lat, LAT_RANGE, numbits)
    val lonBits = findBits(lon, LON_RANGE, numbits + rem)
    val bits = Calate.intercalate(lonBits, latBits)
    bits.grouped(5).map(Base32.toBase32).mkString // scalastyle:ignore
  }

  private def findBits(part: Double, bounds: Bounds, p: Int): List[Boolean] = {
    if (p == 0) Nil
    else {
      val avg = mid(bounds)
      if (part >= avg) true :: findBits(part, (avg, bounds._2), p - 1) // >= to match geohash.org encoding
      else false :: findBits(part, (bounds._1, avg), p - 1)
    }
  }

  /**
   * Decode a base32 geohash into a tuple of (lat, lon)
   */
  def decode(hash: String): (Double, Double) = {
    require(Base32.isValid(hash), "Not a valid Base32 number")
    val (odd, even) = Calate.extracalate(Base32.toBits(hash))
    val lon = mid(decodeBits(LON_RANGE, odd))
    val lat = mid(decodeBits(LAT_RANGE, even))
    (lat, lon)
  }

  private def decodeBits(bounds: Bounds, bits: Seq[Boolean]) =
    bits.foldLeft(bounds)((acc, bit) => if (bit) (mid(acc), acc._2) else (acc._1, mid(acc)))

}
