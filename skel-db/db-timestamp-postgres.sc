import $ivy.`io.getquill::quill-jdbc:3.5.2`
//import $ivy.`io.getquill::quill-async:3.5.2`

import $ivy.`org.postgresql:postgresql:42.2.8`
import $ivy.`com.opentable.components:otj-pg-embedded:0.13.1`

import $ivy.`com.typesafe:config:1.4.1`

import java.time._
import collection.JavaConverters._ 

import com.typesafe.config._

// Postgres
val config = """ctx.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
ctx.dataSource.user=postgres
ctx.dataSource.password=
ctx.dataSource.databaseName=postgres
ctx.dataSource.portNumber=15432
ctx.dataSource.serverName=localhost
ctx.connectionTimeout=30000"""

val typesafeConfig = ConfigFactory.parseMap(config.split("\\n").map(s=>{val p=s.split("="); p(0) -> (if(p.size<2) "" else p(1))}).toMap.asJava)

import io.getquill._

import com.opentable.db.postgres.embedded.EmbeddedPostgres
val server = EmbeddedPostgres.builder().setPort(15432).start()
val ctx = new PostgresJdbcContext(SnakeCase, typesafeConfig.getConfig("ctx"))
//val ctx = new PostgresAsyncContext(SnakeCase, typesafeConfig.getConfig("ctx"))


import ctx._

// Postgres
// https://www.postgresql.org/docs/9.1/datatype-datetime.html
ctx.executeAction(s"CREATE TABLE local_times( t TIMESTAMP );")

// -------------- LocalDateTime 
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

// ------------------ ZonedTimes
// Always stores in UTC
ctx.executeAction(s"CREATE TABLE zoned_times( t TIMESTAMP );") 
case class ZonedTimes(t:ZonedDateTime)

//implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => z.toLocalDateTime)
//implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault() ))
def utc(z:ZonedDateTime) = z.withZoneSameInstant( ZoneId.of("UTC"))
def local(d:LocalDateTime) = d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault())

implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))

ctx.run(quote { query[ZonedTimes].insert(lift(ZonedTimes(ZonedDateTime.now))) })
val zonedTimes = ctx.run(quote { query[ZonedTimes] })
