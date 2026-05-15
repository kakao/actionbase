package com.kakao.actionbase.pipeline.app

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SparkPiDemoTest extends SparkTest {

  @Test
  def testEstimatePiIsCloseToPi(): Unit = {
    val pi = SparkPiDemo.estimatePi(spark, slices = 2)
    assertTrue(pi > 3.0 && pi < 3.3, s"Estimated Pi=$pi out of expected range [3.0, 3.3]")
  }
}
