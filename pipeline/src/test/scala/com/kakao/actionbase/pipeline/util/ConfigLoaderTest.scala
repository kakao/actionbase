package com.kakao.actionbase.pipeline.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.Test

case class SimpleConfig(
    booleanBoolean: Boolean
)
case class PartialConfig(
    booleanBoolean: Boolean,
    byteByte: Byte = 1
)

case class DefaultConfig(
    booleanBoolean: Boolean = true,
    byteByte: Byte = 1,
    charChar: Char = 'a',
    shortShort: Short = 2,
    intInt: Int = 3,
    longLong: Long = 4L,
    floatFloat: Float = 5.0f,
    doubleDouble: Double = 6.0,
    stringString: String = "string",
    booleanBooleanArray: Array[Boolean] = Array(true, false),
    byteArray: Array[Byte] = Array(1, 2, 3),
    charArray: Array[Char] = Array('a', 'b', 'c'),
    shortArray: Array[Short] = Array(1, 2, 3),
    intArray: Array[Int] = Array(1, 2, 3),
    longArray: Array[Long] = Array(1L, 2L, 3L),
    floatArray: Array[Float] = Array(1.0f, 2.0f, 3.0f),
    doubleArray: Array[Double] = Array(1.0, 2.0, 3.0),
    stringArray: Array[String] = Array("string1", "string2", "string3")
)

class ConfigLoaderTest {

  @Test
  def testAllMissing(): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => {
        ConfigLoader.load[SimpleConfig]()
      }
    )
  }

  @Test
  def testAllMissing2(): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => {
        ConfigLoader.load[PartialConfig]()
      }
    )
  }

  @Test
  def testOnlyDefault(): Unit = {
    val config = ConfigLoader.load[DefaultConfig]()

    assertEquals(true, config.booleanBoolean)
    assertEquals(1, config.byteByte)
    assertEquals('a', config.charChar)
    assertEquals(2, config.shortShort)
    assertEquals(3, config.intInt)
    assertEquals(4L, config.longLong)
    assertEquals(5.0f, config.floatFloat)
    assertEquals(6.0, config.doubleDouble)
    assertEquals("string", config.stringString)
    assertEquals(Seq(true, false), config.booleanBooleanArray.toSeq)
    assertEquals(Seq(1, 2, 3), config.byteArray.toSeq)
    assertEquals(Seq('a', 'b', 'c'), config.charArray.toSeq)
    assertEquals(Seq(1, 2, 3), config.shortArray.toSeq)
    assertEquals(Seq(1, 2, 3), config.intArray.toSeq)
    assertEquals(Seq(1L, 2L, 3L), config.longArray.toSeq)
    assertEquals(Seq(1.0f, 2.0f, 3.0f), config.floatArray.toSeq)
    assertEquals(Seq(1.0, 2.0, 3.0), config.doubleArray.toSeq)
    assertEquals(Seq("string1", "string2", "string3"), config.stringArray.toSeq)
  }

  @Test
  def testProvideMissingValue(): Unit = {
    val config = ConfigLoader.load[PartialConfig](Array("--booleanBoolean", "true"))
    assertEquals(true, config.booleanBoolean)
    assertEquals(1, config.byteByte)
  }

  @Test
  def testReplaceValue(): Unit = {
    val config = ConfigLoader.load[DefaultConfig](Array("--booleanBoolean", "false"))

    assertEquals(false, config.booleanBoolean)
    assertEquals(1, config.byteByte)
  }

  @Test
  def testReplaceBoolean(): Unit = {
    val base = ConfigLoader.load[DefaultConfig]()
    assertEquals(true, base.booleanBoolean)

    val replaceByArgs = ConfigLoader.load[DefaultConfig](Array("--booleanBoolean", "false"))
    assertEquals(false, replaceByArgs.booleanBoolean)

    System.setProperty("spark.ab.boolean.boolean", "false")
    val replaceByProps = ConfigLoader.load[DefaultConfig]()
    assertEquals(false, replaceByProps.booleanBoolean)
    System.clearProperty("spark.ab.boolean.boolean")
  }

  @Test
  def testReplaceArrayInt(): Unit = {
    val base = ConfigLoader.load[DefaultConfig]()
    assertEquals(Seq(1, 2, 3), base.intArray.toSeq)

    val replaceByArgs = ConfigLoader.load[DefaultConfig](Array("--intArray", "[4,5,6]"))
    assertEquals(Seq(4, 5, 6), replaceByArgs.intArray.toSeq)

    System.setProperty("spark.ab.int.array", "[7,8,9]")
    val replaceByProps = ConfigLoader.load[DefaultConfig]()
    assertEquals(Seq(7, 8, 9), replaceByProps.intArray.toSeq)
    System.clearProperty("spark.ab.int.array")
  }
}
