package com.kakao.actionbase.pipeline.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test

case class WithSecret(
    name: String = "alice",
    token: String = "tok-secret-value",
    apiPassword: String = "hunter2",
    intArray: Array[Int] = Array(1, 2, 3)
)

case class Inner(
    secret: String = "inner-secret",
    label: String = "inner-label"
)

case class Outer(
    name: String = "outer",
    inner: Inner = Inner()
)

class ConfigPrinterTest {

  @Test
  def testIsSensitiveMatchesCommonNames(): Unit = {
    assertTrue(ConfigPrinter.isSensitive("password"))
    assertTrue(ConfigPrinter.isSensitive("Password"))
    assertTrue(ConfigPrinter.isSensitive("apiPassword"))
    assertTrue(ConfigPrinter.isSensitive("passwd"))
    assertTrue(ConfigPrinter.isSensitive("token"))
    assertTrue(ConfigPrinter.isSensitive("authToken"))
    assertTrue(ConfigPrinter.isSensitive("secret"))
    assertTrue(ConfigPrinter.isSensitive("clientSecret"))
    assertTrue(ConfigPrinter.isSensitive("credential"))
    assertTrue(ConfigPrinter.isSensitive("dbCredentials"))
  }

  @Test
  def testIsSensitiveLeavesInnocuousNamesAlone(): Unit = {
    assertFalse(ConfigPrinter.isSensitive("name"))
    assertFalse(ConfigPrinter.isSensitive("intArray"))
    assertFalse(ConfigPrinter.isSensitive("apiKey"))    // intentionally not masked
    assertFalse(ConfigPrinter.isSensitive("auth"))      // intentionally not masked
    assertFalse(ConfigPrinter.isSensitive("userId"))
  }

  @Test
  def testMaskedTreeReplacesSensitiveTopLevelFields(): Unit = {
    val tree = ConfigPrinter.maskedTree(WithSecret()).asInstanceOf[com.fasterxml.jackson.databind.node.ObjectNode]

    assertEquals("alice", tree.get("name").asText())
    assertEquals("***", tree.get("token").asText())
    assertEquals("***", tree.get("apiPassword").asText())
    assertEquals(3, tree.get("intArray").size())
    assertEquals(1, tree.get("intArray").get(0).asInt())
  }

  @Test
  def testMaskedTreeMasksNestedSensitiveFields(): Unit = {
    val tree = ConfigPrinter.maskedTree(Outer()).asInstanceOf[com.fasterxml.jackson.databind.node.ObjectNode]

    assertEquals("outer", tree.get("name").asText())
    val inner = tree.get("inner")
    assertEquals("***", inner.get("secret").asText())
    assertEquals("inner-label", inner.get("label").asText())
  }

  @Test
  def testReportMasksSensitiveValuesInStdout(): Unit = {
    val baos   = new java.io.ByteArrayOutputStream()
    val oldOut = System.out
    System.setOut(new java.io.PrintStream(baos))
    try {
      ConfigPrinter.printConfigReport(
        envMap = Map.empty,
        propsMap = Map.empty,
        argsMap = Map("token" -> "tok-secret-value", "name" -> "alice"),
        parsed = WithSecret()
      )
    } finally {
      System.setOut(oldOut)
    }
    val output = baos.toString

    assertTrue(output.contains("name = alice"), s"name should be visible:\n$output")
    assertTrue(output.contains("token = ***"), s"token should be masked:\n$output")
    assertTrue(output.contains("args=***"), s"args trace should be masked:\n$output")
    assertFalse(output.contains("tok-secret-value"), s"raw secret leaked:\n$output")
    assertFalse(output.contains("hunter2"), s"raw password leaked:\n$output")
  }
}
