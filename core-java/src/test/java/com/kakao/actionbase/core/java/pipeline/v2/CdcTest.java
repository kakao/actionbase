package com.kakao.actionbase.core.java.pipeline.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kakao.actionbase.core.java.compat.v2.EdgeOperation;
import com.kakao.actionbase.core.java.pipeline.CDC;
import com.kakao.actionbase.core.java.pipeline.CDCV2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CdcTest {
  ObjectMapper objectMapper;

  static final String V2_CDC_JSON_STRING =
      "{\"label\":\"test_service.test_label_20250415_000000\",\"edge\":{\"ts\":1744667998065,\"src\":100,\"tgt\":200,\"props\":{\"property1\":\"1\"},\"traceId\":\"01JRV51ZV7MX92D9M3Y47CE7A7\"},\"op\":\"UPDATE\",\"status\":\"IDLE\",\"before\":{\"active\":false,\"ts\":1744667983563,\"src\":100,\"tgt\":200,\"props\":{\"property1\":\"1\"}},\"after\":{\"active\":false,\"ts\":1744667998065,\"src\":100,\"tgt\":200,\"props\":{\"property1\":\"1\"}},\"acc\":0,\"alias\":\"test_service.gift_received_v2\",\"message\":null,\"audit\":{\"actor\":\"AsyncRequestProcessor\"},\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\",\"ts\":1744668000110,\"tenant\":\"kc\",\"phase\":\"kc\",\"version\":\"2\"}";
  static final String V2_CDC_JSON_ALL_NULLABLE_STRING =
      "{\"label\":\"test_service.test_label_20250415_000000\",\"edge\":{\"ts\":1744667998065,\"src\":100,\"tgt\":200,\"props\":{\"property1\":\"1\"},\"traceId\":\"01JRV51ZV7MX92D9M3Y47CE7A7\"},\"op\":\"UPDATE\",\"status\":\"IDLE\",\"before\":null,\"after\":null,\"acc\":0,\"alias\":null,\"message\":null,\"audit\":{\"actor\":\"AsyncRequestProcessor\"},\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\",\"ts\":1744668000110,\"tenant\":\"kc\",\"phase\":\"kc\",\"version\":\"2\"}";

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("CDC object deserialization test - when all fields are set")
  void testDeserializeWithAllFields() throws JsonProcessingException {
    // when
    CDC cdc = objectMapper.readValue(V2_CDC_JSON_STRING, CDC.class);
    CDCV2 cdcV2 = (CDCV2) cdc;

    // then
    assertInstanceOf(CDCV2.class, cdcV2);
    assertCdcCommonFields(cdcV2);
    assertEquals("test_service.gift_received_v2", cdcV2.alias());
  }

  @Test
  @DisplayName("CDC object deserialization test - when nullable fields are null")
  void testDeserializeWithNullableFields() throws JsonProcessingException {
    // when
    CDC cdc = objectMapper.readValue(V2_CDC_JSON_ALL_NULLABLE_STRING, CDC.class);
    CDCV2 cdcV2 = (CDCV2) cdc;

    // then
    assertCdcCommonFields(cdcV2);
    assertNullableFields(cdcV2);
  }

  private void assertCdcCommonFields(CDCV2 cdc) {
    assertEquals("test_service.test_label_20250415_000000", cdc.label());
    assertEquals(EdgeOperation.UPDATE, cdc.op());
    assertEquals("IDLE", cdc.status());
    assertEquals(0, cdc.acc());
    assertEquals("AsyncRequestProcessor", cdc.audit().actor());
    assertEquals("41c4adba5cf86e0c555e10719729f8ab", cdc.requestId());
    assertEquals(1744668000110L, cdc.ts());
    assertEquals("kc", cdc.tenant());
    assertEquals("kc", cdc.phase());
    assertEquals("2", cdc.version());
  }

  private void assertNullableFields(CDCV2 cdc) {
    assertNull(cdc.before());
    assertNull(cdc.after());
    assertNull(cdc.alias());
    assertNull(cdc.message());
  }
}
