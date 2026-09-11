package io.syspulse.skel.wf.ext.event

import scala.jdk.CollectionConverters._
import scala.util.Try
import java.time.Instant

import spray.json._

/**
 * Alert document stored in OpenSearch (`detector-alert-search` by default).
 *
 * Elastic `_id` is `{deid}:{eid}` (same as the existing detector-alert-search indices).
 * Field names follow the Alert object mapping from Event API parameters:
 *   ts, eid, tx(rid), teid(oid), prid(pid), deid(did), sna(nid), ana(name),
 *   sid, nse(sev), se(from sev), ame(desc), meta, tags(dt), wid (new).
 */
final case class Alert(
  id: String,
  ts: Long,
  eid: String,
  tx: Option[String],
  teid: Long,
  prid: Long,
  deid: Long,
  coid: Long,
  sna: String,
  ana: String,
  sid: String,
  nse: Double,
  ame: String,
  meta: Option[JsObject],
  wid: Option[String],
  dt: Seq[String] = Seq.empty,
  se: String = "",
)

object Alert {
  val DEF_SID = "WORKFLOW"

  def elasticId(deid: Long, eid: String): String = s"${deid}:${eid}"

  def fromCreate(req: EventCreateReq): Alert = {
    val sid = req.sid.map(_.trim).filter(_.nonEmpty).getOrElse(DEF_SID)
    Alert(
      id = elasticId(req.did, req.eid),
      ts = req.ts,
      eid = req.eid,
      tx = req.rid.map(_.trim).filter(_.nonEmpty),
      teid = req.oid,
      prid = req.pid,
      deid = req.did,
      coid = req.cid,
      sna = req.nid,
      ana = req.name.getOrElse(""),
      sid = sid,
      nse = req.sev,
      ame = req.desc.getOrElse(""),
      meta = Some(req.meta.getOrElse(JsObject.empty)),
      wid = req.wid.map(_.trim).filter(_.nonEmpty),
      dt = req.tags.getOrElse(Seq.empty).map(_.trim).filter(_.nonEmpty),
      se = Severity.label(req.sev),
    )
  }

  /** Source JSON written to OpenSearch (no `id`; `_id` is set on the request). */
  def sourceJson(a: Alert): JsObject = {
    val fields = scala.collection.mutable.ListBuffer[(String, JsValue)](
      "ts" -> JsNumber(a.ts),
      "eid" -> JsString(a.eid),
      "teid" -> JsNumber(a.teid),
      "prid" -> JsNumber(a.prid),
      "deid" -> JsNumber(a.deid),
      "coid" -> JsNumber(a.coid),
      "sna" -> JsString(a.sna),
      "ana" -> JsString(a.ana),
      "sid" -> JsString(a.sid),
      "nse" -> JsNumber(a.nse),
      "se"  -> JsString(a.se),
      "ame" -> JsString(a.ame),
      "dt"  -> JsArray(a.dt.map(JsString(_)).toVector),
      "meta" -> a.meta.getOrElse(JsObject.empty),
    )
    a.tx.foreach(v => fields += ("tx" -> JsString(v)))
    a.wid.foreach(v => fields += ("wid" -> JsString(v)))
    JsObject(fields.toMap)
  }

  def fromHit(id: String, source: Map[String, Any]): Alert = Alert(
    id = id,
    ts = tsOf(source.get("ts")),
    eid = strOf(source.get("eid")),
    tx = optStr(source.get("tx")),
    teid = longOf(source.get("teid")),
    prid = longOf(source.get("prid")),
    deid = longOf(source.get("deid")),
    coid = longOf(source.get("coid")),
    sna = strOf(source.get("sna")),
    ana = strOf(source.get("ana")),
    sid = strOf(source.get("sid"), DEF_SID),
    nse = doubleOf(source.get("nse")),
    ame = strOf(source.get("ame")),
    meta = jsObjectOf(source.get("meta")),
    wid = optStr(source.get("wid")),
    dt = strsOf(source.get("dt")),
    se = source.get("se") match {
      case None | Some(null) => Severity.label(doubleOf(source.get("nse")))
      case Some(s: String) => s
      case Some(other) => other.toString
    },
  )

  def fromSourceJson(id: String, json: String): Alert = {
    val js = json.parseJson.asJsObject
    val m = js.fields
    Alert(
      id = id,
      ts = tsOf(jsVal(m.get("ts"))),
      eid = strOf(jsVal(m.get("eid"))),
      tx = optStr(jsVal(m.get("tx"))),
      teid = longOf(jsVal(m.get("teid"))),
      prid = longOf(jsVal(m.get("prid"))),
      deid = longOf(jsVal(m.get("deid"))),
      coid = longOf(jsVal(m.get("coid"))),
      sna = strOf(jsVal(m.get("sna"))),
      ana = strOf(jsVal(m.get("ana"))),
      sid = strOf(jsVal(m.get("sid")), DEF_SID),
      nse = doubleOf(jsVal(m.get("nse"))),
      ame = strOf(jsVal(m.get("ame"))),
      meta = m.get("meta").collect { case o: JsObject => o },
      wid = optStr(jsVal(m.get("wid"))),
      dt = strsOf(jsVal(m.get("dt")).orElse(m.get("dt"))),
      se = m.get("se") match {
        case None | Some(JsNull) => Severity.label(doubleOf(jsVal(m.get("nse"))))
        case Some(JsString(s)) => s
        case Some(other) => other.toString
      },
    )
  }

  private def jsVal(v: Option[JsValue]): Option[Any] = v.map {
    case JsNumber(n) => n
    case JsString(s) => s
    case JsBoolean(b) => b
    case o: JsObject => o
    case JsNull => null
    case other => other
  }

  private def tsOf(v: Option[Any]): Long = v match {
    case None | Some(null) => 0L
    case Some(n: Number) => n.longValue
    case Some(s: String) =>
      Try(s.toLong).orElse(Try(Instant.parse(s).toEpochMilli)).getOrElse(0L)
    case Some(other) => Try(other.toString.toLong).getOrElse(0L)
  }

  private def longOf(v: Option[Any]): Long = v match {
    case None | Some(null) => 0L
    case Some(n: Number) => n.longValue
    case Some(s: String) => Try(s.trim.toLong).getOrElse(0L)
    case Some(other) => Try(other.toString.toLong).getOrElse(0L)
  }

  private def doubleOf(v: Option[Any]): Double = v match {
    case None | Some(null) => 0.0
    case Some(n: Number) => n.doubleValue
    case Some(s: String) => Try(s.trim.toDouble).getOrElse(0.0)
    case Some(other) => Try(other.toString.toDouble).getOrElse(0.0)
  }

  private def strOf(v: Option[Any], dflt: String = ""): String = v match {
    case None | Some(null) => dflt
    case Some(s: String) => s
    case Some(other) => other.toString
  }

  private def strsOf(v: Option[Any]): Seq[String] = v match {
    case None | Some(null) => Seq.empty
    case Some(xs: java.util.List[_]) =>
      xs.asScala.toSeq.flatMap(x => Option(x).map(_.toString).map(_.trim).filter(_.nonEmpty))
    case Some(xs: Seq[_]) =>
      xs.flatMap(x => Option(x).map {
        case JsString(s) => s
        case other => other.toString
      }.map(_.trim).filter(_.nonEmpty))
    case Some(JsArray(elements)) =>
      elements.collect { case JsString(s) if s.trim.nonEmpty => s.trim }
    case Some(s: String) if s.trim.nonEmpty => Seq(s.trim)
    case _ => Seq.empty
  }

  private def optStr(v: Option[Any]): Option[String] = v match {
    case None | Some(null) => None
    case Some(s: String) => Option(s).map(_.trim).filter(_.nonEmpty)
    case Some(other) => Option(other.toString).map(_.trim).filter(_.nonEmpty)
  }

  private def jsObjectOf(v: Option[Any]): Option[JsObject] = v match {
    case None | Some(null) => None
    case Some(o: JsObject) => Some(o)
    case Some(m: java.util.Map[_, _]) =>
      Some(JsObject(m.asScala.toMap.map { case (k, vv) => k.toString -> anyToJs(vv) }))
    case Some(m: Map[_, _]) =>
      Some(JsObject(m.map { case (k, vv) => k.toString -> anyToJs(vv) }))
    case Some(s: String) => Try(s.parseJson.asJsObject).toOption
    case _ => None
  }

  private def anyToJs(v: Any): JsValue = v match {
    case null => JsNull
    case o: JsValue => o
    case n: java.lang.Integer => JsNumber(n.intValue)
    case n: java.lang.Long => JsNumber(n.longValue)
    case n: java.lang.Double => JsNumber(n.doubleValue)
    case n: java.lang.Float => JsNumber(n.floatValue)
    case n: java.lang.Number => JsNumber(n.doubleValue)
    case b: java.lang.Boolean => JsBoolean(b.booleanValue)
    case s: String => JsString(s)
    case m: java.util.Map[_, _] => JsObject(m.asScala.toMap.map { case (k, vv) => k.toString -> anyToJs(vv) })
    case xs: java.util.List[_] => JsArray(xs.asScala.map(anyToJs).toVector)
    case other => JsString(other.toString)
  }
}
