package actionbase.core.model

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

/**
  * Shared Jackson configuration used by every OSS pipeline call site that
  * reads or writes ActionBase JSON (AbEdge, AbWal, AbServiceImpl,
  * AbAdminServiceImpl, V3AbServiceImpl, V3AbServiceFake, and test fixtures).
  *
  * Configures Jackson for ActionBase DTOs (Scala module, lenient unknown
  * properties, strict primitive nulls). Callers that need a plain Java
  * mapper for the Scala → Java property-map bridge should read [[java]].
  *
  * `ObjectMapper` is thread-safe after configuration, so a single lazy
  * singleton per variant is sufficient — call sites should reference
  * [[scala]] or [[java]] directly rather than copying the instance.
  */
object ActionbaseObjectMapper {

  /**
    * Scala-aware mapper.
    *
    *   - Lenient about unknown properties: the service returns additional
    *     fields over time that older clients must tolerate.
    *   - Strict about null primitives: number fields decoding `null` must
    *     fail rather than silently become 0.
    *   - Strict about null creator properties: required constructor args
    *     decoding `null` must fail rather than instantiate with a null.
    */
  lazy val scala: ObjectMapper with ScalaObjectMapper = {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
    mapper
  }

  /**
    * Plain Java mapper without the Scala module. Used by the Scala → Java
    * conversion bridge (e.g. [[AbEdge.createJavaBulkLoadEdge]]) where
    * nested Scala collections must decode into `java.util.Map` rather than
    * Scala `Map`. Do not register [[DefaultScalaModule]] here or that
    * semantics breaks.
    */
  lazy val java: ObjectMapper = new ObjectMapper()
}
