import $ivy.`io.getquill::quill-jdbc:3.5.2`

import $ivy.`mysql:mysql-connector-java:8.0.22`

import $ivy.`com.typesafe:config:1.4.1`

import java.time._
import collection.JavaConverters._ 

import com.typesafe.config._

val config = """ctx.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
ctx.dataSource.url=jdbc:mysql://172.17.0.1:3306/shop_db
ctx.dataSource.user=shop_user
ctx.dataSource.password=shop_pass
ctx.connectionTimeout=30000
ctx.idleTimeout=30000
ctx.minimumIdle=5
ctx.maximumPoolSize=20
ctx.poolName=DB-Pool
ctx.maxLifetime=2000000"""

val typesafeConfig = ConfigFactory.parseMap(config.split("\\n").map(s=>{val p=s.split("="); p(0) -> (if(p.size<2) "" else p(1))}).toMap.asJava)

import io.getquill._

// reads from application.properties on CLASSPATH
//val ctx = new MysqlJdbcContext(SnakeCase, "ctx")
// reads from Config
val ctx = new MysqlJdbcContext(SnakeCase, typesafeConfig.getConfig("ctx"))

import ctx._

// ============================================ Local
ctx.executeAction(s"CREATE TABLE IF NOT EXISTS local_times( t TIMESTAMP(6) );")
case class LocalTimes(t:LocalDateTime)

ctx.run(quote { query[LocalTimes].insert(lift(LocalTimes(LocalDateTime.now))) })
val localTimes = ctx.run(quote { query[LocalTimes] })

// ALWAYS store time in UTC
// working with DataTime as Java LocalDateTime 
val t1 = LocalTimes(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime)
ctx.run(quote { query[LocalTimes].insert(lift(t1)) }) 

// get into Local time (ZonedDateTime with proper zone)
localTimes.map(t =>t.t.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault() ))
localTimes.map(t =>t.t.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.of( "Europe/Kiev") ))


// ============================================= Zoned -> DATETIME
def utc(z:ZonedDateTime) = z.withZoneSameInstant( ZoneId.of("UTC"))
def local(d:LocalDateTime) = d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault())

ctx.executeAction(s"CREATE TABLE IF NOT EXISTS zoned_times( t DATETIME(3) );")
case class ZonedTimes(t:ZonedDateTime)

implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))

ctx.run(quote { query[ZonedTimes].insert(lift(ZonedTimes(ZonedDateTime.now))) })
val zonedTimes = ctx.run(quote { query[ZonedTimes] })

// ============================================= Zoned as DateTime
ctx.executeAction(s"CREATE TABLE IF NOT EXISTS zoned_timestamps( t TIMESTAMP(6) );") 
case class ZonedTimestamps(t:ZonedDateTime)

implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))

ctx.run(quote { query[ZonedTimestamps].insert(lift(ZonedTimestamps(ZonedDateTime.now))) })
val zonedTimestamps = ctx.run(quote { query[ZonedTimestamps] })
