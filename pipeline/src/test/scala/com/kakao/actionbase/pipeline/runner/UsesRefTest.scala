package com.kakao.actionbase.pipeline.runner

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class UsesRefTest {

  @Test
  def parsesGhaShortFormToMavenCoord(): Unit = {
    val ref = UsesRef.parse("actionbase/pipeline@v1")
    assertEquals("com.kakao.actionbase", ref.group)
    assertEquals("pipeline", ref.artifact)
    assertEquals("v1", ref.version)
    assertEquals("spark", ref.kind)
    assertEquals(None, ref.mainClass)
  }

  @Test
  def parsesClassSuffixInBothForms(): Unit = {
    val gha = UsesRef.parse("actionbase/pipeline@0.x:SparkPiJob")
    assertEquals(Some("SparkPiJob"), gha.mainClass)

    val maven = UsesRef.parse("com.kakao.actionbase:pipeline:0.x:SparkPiJob")
    assertEquals(Some("SparkPiJob"), maven.mainClass)

    val fqn = UsesRef.parse("actionbase/pipeline@0.x:com.example.MyJob")
    assertEquals(Some("com.example.MyJob"), fqn.mainClass)
  }

  @Test
  def ghaRefAcceptsSemverVersion(): Unit = {
    val ref = UsesRef.parse("actionbase/pipeline@0.3.0")
    assertEquals("0.3.0", ref.version)
  }

  @Test
  def acceptsLatestAsVersion(): Unit = {
    val ref = UsesRef.parse("actionbase/pipeline@latest")
    assertEquals("latest", ref.version)
    assertEquals("com.kakao.actionbase:pipeline:latest", ref.coord)
  }

  @Test
  def acceptsNpmStyleRangeVersions(): Unit = {
    assertEquals("0.x", UsesRef.parse("actionbase/pipeline@0.x").version)
    assertEquals("0.3.x", UsesRef.parse("actionbase/pipeline@0.3.x").version)
    assertEquals("0.2.0-SNAPSHOT", UsesRef.parse("actionbase/pipeline@0.2.0-SNAPSHOT").version)
  }

  @Test
  def parsesMavenCoordDirectly(): Unit = {
    val ref = UsesRef.parse("com.kakao.actionbase:pipeline:0.3.0-SNAPSHOT")
    assertEquals("com.kakao.actionbase", ref.group)
    assertEquals("pipeline", ref.artifact)
    assertEquals("0.3.0-SNAPSHOT", ref.version)
    assertEquals("spark", ref.kind)
  }

  @Test
  def ghaAndMavenFormsResolveIdentically(): Unit = {
    val gha   = UsesRef.parse("actionbase/pipeline@v1")
    val maven = UsesRef.parse("com.kakao.actionbase:pipeline:v1")
    assertEquals(gha, maven)
  }

  @Test
  def coordAssemblesGroupArtifactVersion(): Unit = {
    val ref = UsesRef.parse("actionbase/pipeline@0.3.0")
    assertEquals("com.kakao.actionbase:pipeline:0.3.0", ref.coord)
  }

  @Test
  def rejectsUnknownOwnerAlias(): Unit = {
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => UsesRef.parse("unknown-owner/pipeline@v1")
    )
    assertTrue(ex.getMessage.contains("unknown-owner"), ex.getMessage)
  }

  @Test
  def rejectsMalformedReference(): Unit = {
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => UsesRef.parse("not-a-ref")
    )
    assertTrue(ex.getMessage.contains("must be"), ex.getMessage)
  }
}
