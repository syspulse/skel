package io.syspulse.skel.ingest.flow

import scala.util.{Success,Failure,Try}
import scala.jdk.CollectionConverters._

// import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import io.getquill._
import io.getquill.context._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.context.jdbc.JdbcContext

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.{Context => MacroContext}

// https://stackoverflow.com/questions/44784310/how-to-write-generic-function-with-scala-quill-io-library/44797199#44797199
class InsertOrUpdateMacro(val c: MacroContext) {
  import c.universe._

  def insertOrUpdate[T](entity: Tree, filter: Tree)(implicit t: WeakTypeTag[T]): Tree =
    q"""
      import ${c.prefix}._
      val updateQuery = ${c.prefix}.quote {
        ${c.prefix}.query[$t].filter($filter).update(lift($entity))
      }
      val insertQuery = quote {
        query[$t].insert(lift($entity))
      }
      run(${c.prefix}.query[$t].filter($filter)).size match {
          case 1 => run(updateQuery)
          case _ => run(insertQuery)
      }
      ()
    """

  def insertOnly[T](entity: Tree)(implicit t: WeakTypeTag[T]): Tree =
    q"""
      import ${c.prefix}._
      val insertQuery = quote {
        query[$t].insert(lift($entity))
      }
      run(insertQuery)
      ()
    """
}

trait Queries {
  this: JdbcContext[_, _] =>
  def insertOrUpdate[T](entity: T, filter: (T) => Boolean): Unit = macro InsertOrUpdateMacro.insertOrUpdate[T]
  def insertOnly[T](entity: T): Unit = macro InsertOrUpdateMacro.insertOnly[T]
}

