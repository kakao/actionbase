package actionbase.core.model

import actionbase.pipeline.testsupport.BaseTest

class AbWalTest extends BaseTest {
  test("from json (contain all fields)") {
    val json = """{
                 |  "alias":"example.example_label_v1",
                 |  "label":"example.example_label_v1_20260101_000000",
                 |  "edge":{
                 |    "ts":1700000000000,
                 |    "src":1001,
                 |    "tgt":"2:2001",
                 |    "props":{
                 |      "service_id":2
                 |    },
                 |    "traceId":"01TESTTRACE000000000000000"
                 |  },
                 |  "op":"INSERT",
                 |  "audit": { "actor": "default" },
                 |  "requestId": "00000000000000000000000000000000",
                 |  "ts": 1700000000001,
                 |  "phase":"beta",
                 |  "version":"v1"
                 |}""".stripMargin
    val log  = AbWal.parse(json).get
    log.alias shouldBe Some("example.example_label_v1")
    log.label shouldBe "example.example_label_v1_20260101_000000"
    log.op.toString shouldBe "INSERT"
    log.phase shouldBe "beta"
    log.version shouldBe "v1"
    log.edge.getTs shouldBe 1700000000000L
    log.edge.getSrc.toString shouldBe "1001"
    log.edge.getTgt.toString shouldBe "2:2001"
    log.edge.getTraceId shouldBe "01TESTTRACE000000000000000"
    log.edge.getProps.size shouldBe 1
    log.edge.getProps.get("service_id") shouldBe 2
  }

  test("from json (contain all fields with empty props field)") {
    val json = """{
                 |  "alias":"example.example_label_v1",
                 |  "label":"example.example_label_v1_20260101_000000",
                 |  "edge":{
                 |    "ts":1700000000000,
                 |    "src":1001,
                 |    "tgt":"2:2001",
                 |    "props":{},
                 |    "traceId":"01TESTTRACE000000000000000"
                 |  },
                 |  "op":"INSERT",
                 |  "audit": { "actor": "default" },
                 |  "requestId": "00000000000000000000000000000000",
                 |  "ts": 1700000000001,
                 |  "phase":"beta",
                 |  "version":"v1"
                 |}""".stripMargin
    val log  = AbWal.parse(json).get
    log.alias shouldBe Some("example.example_label_v1")
    log.label shouldBe "example.example_label_v1_20260101_000000"
    log.op.toString shouldBe "INSERT"
    log.phase shouldBe "beta"
    log.version shouldBe "v1"
    log.edge.getTs shouldBe 1700000000000L
    log.edge.getSrc.toString shouldBe "1001"
    log.edge.getTgt.toString shouldBe "2:2001"
    log.edge.getTraceId shouldBe "01TESTTRACE000000000000000"
    log.edge.getProps.size shouldBe 0
  }

  // Ignored in OSS: strict "fail on missing props/op" behaviour relied on
  // FAIL_ON_NULL_CREATOR_PROPERTIES / FAIL_ON_NULL_FOR_PRIMITIVES which the
  // shared ActionbaseObjectMapper.scala intentionally does not enable
  // (slim-16 decision). OSS parse tolerates missing edge.props and missing op
  // and returns a partially populated AbWal instead of None.
  test("from json (missing props field)") {
    val json = """{
                 |  "alias":"example.example_label_v1",
                 |  "label":"example.example_label_v1_20260101_000000",
                 |  "edge":{
                 |    "ts":1700000000000,
                 |    "src":1001,
                 |    "tgt":"2:2001",
                 |    "traceId":"01TESTTRACE000000000000000"
                 |  },
                 |  "op":"INSERT",
                 |  "audit": { "actor": "default" },
                 |  "requestId": "00000000000000000000000000000000",
                 |  "ts": 1700000000001,
                 |  "phase":"beta",
                 |  "version":"v1"
                 |}""".stripMargin
    AbWal.parse(json) shouldBe None
  }

  test("from json (missing op)") {
    val json = """{
                 |  "alias":"example.example_label_v1",
                 |  "label":"example.example_label_v1_20260101_000000",
                 |  "edge":{
                 |    "ts":1700000000000,
                 |    "src":1001,
                 |    "tgt":"2:2001",
                 |    "props":{
                 |      "service_id":2
                 |    },
                 |    "traceId":"01TESTTRACE000000000000000"
                 |  },
                 |  "audit": { "actor": "default" },
                 |  "requestId": "00000000000000000000000000000000",
                 |  "ts": 1700000000001,
                 |  "phase":"beta",
                 |  "version":"v1"
                 |}""".stripMargin
    AbWal.parse(json) shouldBe None
  }

  test("from json without alias") {
    val json = """{
                 |  "alias": null,
                 |  "label":"example.example_label_v1_20260101_000000",
                 |  "edge":{
                 |    "ts":1700000000000,
                 |    "src":1001,
                 |    "tgt":"2:2001",
                 |    "props":{
                 |      "service_id":2
                 |    },
                 |    "traceId":"01TESTTRACE000000000000000"
                 |  },
                 |  "audit": { "actor": "default" },
                 |  "requestId": "00000000000000000000000000000000",
                 |  "op":"INSERT",
                 |  "ts": 1700000000001,
                 |  "phase":"beta",
                 |  "version":"v1"
                 |}""".stripMargin
    val log  = AbWal.parse(json).get
    log.alias shouldBe None
    log.label shouldBe "example.example_label_v1_20260101_000000"
    log.op.toString shouldBe "INSERT"
    log.phase shouldBe "beta"
    log.version shouldBe "v1"
    log.edge.getTs shouldBe 1700000000000L
    log.edge.getSrc.toString shouldBe "1001"
    log.edge.getTgt.toString shouldBe "2:2001"
    log.edge.getTraceId shouldBe "01TESTTRACE000000000000000"
    log.edge.getProps.size shouldBe 1
    log.edge.getProps.get("service_id") shouldBe 2
  }
}
