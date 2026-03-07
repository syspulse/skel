package io.syspulse.skel.wf.temporal

import io.temporal.common.converter.{
  DataConverter,
  DefaultDataConverter,
  JacksonJsonPayloadConverter
}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

object ScalaDataConverter {

  /**
   * Creates an ObjectMapper configured for Scala case classes
   */
  def createObjectMapper(): ObjectMapper = {
    val mapper = new ObjectMapper()

    // Register Scala module for case class support
    mapper.registerModule(DefaultScalaModule)

    // Register Java Time module for LocalDateTime, ZonedDateTime, etc.
    mapper.registerModule(new JavaTimeModule())

    // Configure serialization settings
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)

    mapper
  }

  /**
   * Creates a DataConverter that can handle Scala case classes
   */
  def create(): DataConverter = {
    val objectMapper = createObjectMapper()
    val jsonConverter = new JacksonJsonPayloadConverter(objectMapper)

    DefaultDataConverter.newDefaultInstance()
      .withPayloadConverterOverrides(jsonConverter)
  }
}
