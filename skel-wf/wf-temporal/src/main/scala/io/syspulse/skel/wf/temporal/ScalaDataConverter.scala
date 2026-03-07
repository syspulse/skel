package io.syspulse.skel.wf.temporal

import io.temporal.common.converter.{
  DataConverter,
  DefaultDataConverter,
  JacksonJsonPayloadConverter
}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature, DeserializationFeature}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ClassTagExtensions}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor

object ScalaDataConverter {

  /**
   * Creates an ObjectMapper configured for Scala case classes with ClassTagExtensions
   */
  def createObjectMapper(): ObjectMapper = {
    // Create base mapper
    val mapper = new ObjectMapper()

    // Register Scala module with ClassTagExtensions for proper type handling
    mapper.registerModule(DefaultScalaModule)

    // Register Java Time module
    mapper.registerModule(new JavaTimeModule())

    // Apply ClassTagExtensions for better Scala type support
    val extendedMapper = mapper :: ClassTagExtensions

    // Configure visibility - critical for case classes
    extendedMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
    extendedMapper.setVisibility(PropertyAccessor.CREATOR, Visibility.ANY)
    extendedMapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE)
    extendedMapper.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
    extendedMapper.setVisibility(PropertyAccessor.SETTER, Visibility.NONE)

    // Configure serialization settings
    extendedMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    extendedMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    extendedMapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)

    extendedMapper
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
