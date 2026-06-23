package com.kakao.actionbase.pipeline.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
    val config = ConfigLoader.load[PartialConfig](Array("--booleanBoolean=true"))
    assertEquals(true, config.booleanBoolean)
    assertEquals(1, config.byteByte)
  }

  @Test
  def testReplaceValue(): Unit = {
    val config = ConfigLoader.load[DefaultConfig](Array("--booleanBoolean=false"))

    assertEquals(false, config.booleanBoolean)
    assertEquals(1, config.byteByte)
  }

  @Test
  def testReplaceBoolean(): Unit = {
    val base = ConfigLoader.load[DefaultConfig]()
    assertEquals(true, base.booleanBoolean)

    val replaceByArgs = ConfigLoader.load[DefaultConfig](Array("--booleanBoolean=false"))
    assertEquals(false, replaceByArgs.booleanBoolean)

    try {
      System.setProperty("spark.ab.boolean.boolean", "false")
      val replaceByProps = ConfigLoader.load[DefaultConfig]()
      assertEquals(false, replaceByProps.booleanBoolean)
    } finally {
      System.clearProperty("spark.ab.boolean.boolean")
    }
  }

  @Test
  def testReplaceArrayInt(): Unit = {
    val base = ConfigLoader.load[DefaultConfig]()
    assertEquals(Seq(1, 2, 3), base.intArray.toSeq)

    val replaceByArgs = ConfigLoader.load[DefaultConfig](Array("--intArray=[4,5,6]"))
    assertEquals(Seq(4, 5, 6), replaceByArgs.intArray.toSeq)

    try {
      System.setProperty("spark.ab.int.array", "[7,8,9]")
      val replaceByProps = ConfigLoader.load[DefaultConfig]()
      assertEquals(Seq(7, 8, 9), replaceByProps.intArray.toSeq)
    } finally {
      System.clearProperty("spark.ab.int.array")
    }
  }

  @Test
  def testStringArrayWithEscapedComma(): Unit = {
    val config = ConfigLoader.load[DefaultConfig](Array("--stringArray=[a\\,b,c]"))
    assertEquals(Seq("a,b", "c"), config.stringArray.toSeq)
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource(
    Array(
      "--booleanBoolean",     // missing '='
      "booleanBoolean=false", // missing '--' prefix
      "--=value"              // empty key
    )
  )
  def testRejectMalformedArg(arg: String): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => ConfigLoader.load[DefaultConfig](Array(arg))
    )
  }

  // Pipe delimiter so commas inside the array literal stay intact.
  // Empty `expected` means "expected empty array".
  @ParameterizedTest(name = "[{index}] {0} -> [{1}]")
  @CsvSource(
    delimiter = '|',
    value = Array(
      "[]            |",       // empty literal yields empty array
      "[1,2,3]       | 1,2,3", // baseline
      "[1,2,3,]      | 1,2,3", // trailing comma ignored
      "[ , 1 , 2 ]   | 1,2"    // empty/whitespace tokens dropped
    )
  )
  def testIntArrayParsing(literal: String, expected: String): Unit = {
    val config = ConfigLoader.load[DefaultConfig](Array(s"--intArray=$literal"))
    // JUnit5 @CsvSource: an empty unquoted cell becomes null, not "".
    val expectedSeq =
      if (expected == null || expected.trim.isEmpty) Seq.empty[Int]
      else expected.split(',').map(_.trim).filter(_.nonEmpty).map(_.toInt).toSeq
    assertEquals(expectedSeq, config.intArray.toSeq)
  }
}
