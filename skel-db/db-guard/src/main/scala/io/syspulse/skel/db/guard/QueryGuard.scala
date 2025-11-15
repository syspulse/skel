package io.syspulse.skel.db.guard

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

trait QueryGuard {
  def isAllowed(q:String, opts:Map[String,Any] = Map()):Try[Boolean]
}

class PassGuard extends QueryGuard {
  def isAllowed(q:String, opts:Map[String,Any] = Map()):Try[Boolean] = {
    Success(true)
  }
}

object PassGuard extends PassGuard

class ElasticGuard() extends QueryGuard {
  private val log = Logger(this.getClass)

  // DDL keywords that should be blocked
  private val ddlKeywords = Set(
    "CREATE", "DROP", "DELETE", "INSERT", "UPDATE", "ALTER", "TRUNCATE",
    "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL", "MERGE", "UPSERT"
  )
  
  // Dangerous functions that could modify data
  private val dangerousFunctions = Set(
    "DELETE", "INSERT", "UPDATE", "UPSERT", "MERGE", "REPLACE"
  )
  
  def isAllowed(q:String, opts:Map[String,Any] = Map()):Try[Boolean] = {
    try {

      val tenantId = opts.get("tenantId").map(_.toString)
      
      val queryUpper = q.toUpperCase.trim
      
      // Check for DDL statements
      val hasDdl = ddlKeywords.exists(keyword => 
        queryUpper.startsWith(keyword) || queryUpper.contains(s" ${keyword} ")
      )
      
      if (hasDdl) {
        log.warn(s"Blocked DDL statement: ${q}")
        return Failure(new Exception("DDL statements are not allowed"))
      }
      
      // Check for dangerous functions
      val hasDangerousFunction = dangerousFunctions.exists(func =>
        queryUpper.contains(func)
      )
      
      if (hasDangerousFunction) {
        log.warn(s"Blocked dangerous function: ${q}")
        return Failure(new Exception("Data modification functions are not allowed"))
      }
      
      // For SQL queries, check for tenant isolation
      if (queryUpper.contains("SELECT") && tenantId.isDefined) {
        // Ensure tenant isolation - query should reference tenantId
        if (!queryUpper.contains("tenantId") && !queryUpper.contains("TENANTID")) {
          log.warn(s"Query missing tenant isolation: ${q}")
          return Failure(new Exception("Query must include tenant isolation"))
        }
      }
      
      // Check for suspicious patterns
      val suspiciousPatterns = Seq(
        "UNION", "INFORMATION_SCHEMA", "SYS.", "PG_", "MYSQL.",
        "SCRIPT", "EVAL", "EXEC", "JAVASCRIPT", "PAINLESS"
      )
      
      val hasSuspicious = suspiciousPatterns.exists(pattern =>
        queryUpper.contains(pattern)
      )
      
      if (hasSuspicious) {
        log.warn(s"Blocked suspicious pattern: ${q}")
        return Failure(new Exception("Suspicious query patterns detected"))
      }
      
      log.debug(s"Query allowed: ${q}")
      Success(true)
      
    } catch {
      case e: Exception =>
        log.error(s"Error validating query: ${q}", e)
        Failure(e)
    }
  }
}
