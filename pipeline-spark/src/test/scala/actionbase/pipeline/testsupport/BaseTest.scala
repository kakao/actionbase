package actionbase.pipeline.testsupport

import io.circe.Json
import io.circe.parser._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{OutputStream, PrintStream}
import java.util.TimeZone
import scala.io.Source

/**
  * Shared non-Spark scaffolding for the pipeline module tests:
  *   - [[BaseTest]]: scalatest base trait (funsuite + matchers + before-hooks) with
  *     deterministic UTC timezone, temp-dir constants, and stdout/stderr suppression helpers.
  *   - [[JsonAssertions]]: circe-backed JSON structural equality assertion (mixed in via BaseTest).
  *   - [[TestUtil]]: classpath resource reader for JSON fixture tests.
  *
  * Consolidated from the former `BaseTest` / `JsonAssertions` / `TestUtil` / `TestKit`
  * helpers — `TestKit` dropped because it had no call sites.
  */
trait BaseTest extends AnyFunSuite with Matchers with BeforeAndAfter with JsonAssertions {
  // WARN — intentional process-wide JVM state mutation. Every test/IT suite
  // that mixes BaseTest sees UTC for time-based assertions regardless of
  // host TZ. Because the mutation sticks for the whole JVM, suites that
  // need a different TZ must override locally inside their test blocks
  // (not via BaseTest). Idempotent — safe on trait reload.
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

  val tempRootDir: String = s"${System.getProperty("user.dir")}/target"
  val tempHdfsDir: String = s"$tempRootDir/target/local.hdfs"

  def ignoreOutput[R](f: => R): R = {
    val out      = System.out
    val dummyOut = new PrintStream(new OutputStream() {
      override def write(b: Int): Unit = {}
    })
    try {
      System.setOut(dummyOut)
      f
    } finally {
      System.setOut(out)
    }
  }

  def ignoreErrorPrint[R](f: => R): R = {
    val out      = System.err
    val dummyOut = new PrintStream(new OutputStream() {
      override def write(b: Int): Unit = {}
    })
    try {
      System.setErr(dummyOut)
      f
    } finally {
      System.setErr(out)
    }
  }
}

trait JsonAssertions {
  def assertJsonEquals(json1: String, json2: String): Unit = {
    val tree1  = parseOrElse(json1)
    val tree2  = parseOrElse(json2)
    val result = tree1 == tree2
    if (!result) {
      println(s"json1: $tree1")
      println(s"json2: $tree2")
    }
    assert(result)
  }

  private def parseOrElse(jsonString: String): Json = parse(jsonString) match {
    case Right(json)   => json
    case Left(failure) => throw failure
  }
}

object TestUtil {
  def read(path: String): String = {
    val is = getClass.getClassLoader.getResourceAsStream(path)
    Source.fromInputStream(is).getLines().mkString("\n")
  }
}
