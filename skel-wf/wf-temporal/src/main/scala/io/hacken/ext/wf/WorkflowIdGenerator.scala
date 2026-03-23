package io.hacken.ext.wf

import com.typesafe.scalalogging.Logger

/**
 * Utility for generating workflow IDs from title field with placeholder syntax
 *
 * Supported placeholders:
 * - {tid} - transaction/task ID
 * - {pid} - process/project ID
 * - {ts} - timestamp (milliseconds since epoch)
 * - {project} - project name
 * - {title} - WorkflowSchema.title
 * - {name} - WorkflowSchema.name
 *
 * Example title: "{name}-{project}-{ts}"
 * Example result: "PoR2-MyProject-1711234567890"
 */
object WorkflowIdGenerator {
  private val log = Logger(getClass)

  /**
   * Generate workflow ID from title and context
   *
   * @param title Title string with {placeholder} syntax (e.g., "{name}-{project}-{ts}")
   * @param schema WorkflowSchema to extract name and title from
   * @param context Additional context values (tid, pid, project, etc.)
   * @return Generated workflow ID with placeholders replaced
   */
  def generate(
    title: String,
    schema: WorkflowSchema,
    context: Map[String, String] = Map.empty
  ): String = {

    // Build complete context with schema values and provided context
    val fullContext = Map(
      "name" -> schema.name,
      "title" -> schema.title,
      "ts" -> System.currentTimeMillis().toString
    ) ++ context

    // Replace all placeholders
    val result = fullContext.foldLeft(title) { case (current, (key, value)) =>
      current.replace(s"{$key}", value)
    }

    log.info(s"Generated workflow ID: '$result' (from title: '$title')")
    result
  }

  /**
   * Generate workflow ID using schema.title
   *
   * @param schema WorkflowSchema with title field (may contain placeholders)
   * @param context Additional context values
   * @param defaultTemplate Not used - kept for backward compatibility
   * @return Generated workflow ID
   */
  def generateFromSchema(
    schema: WorkflowSchema,
    context: Map[String, String] = Map.empty,
    defaultTemplate: String = "{name}-{ts}"
  ): String = {
    // ALWAYS use schema.title (no placeholder check)
    log.info(s"Using schema.title: '${schema.title}'")
    generate(schema.title, schema, context)
  }
}
