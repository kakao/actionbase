package com.kakao.actionbase.v2.core.metadata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BucketTests {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldFormatEpochMillisAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.MILLISECOND, "Asia/Seoul", "yyyy-MM-dd");

    // 2026-06-11T00:00:00+09:00
    long epochMillis = 1781103600000L;

    assertEquals("2026-06-11", bucket.apply(epochMillis));
  }

  @Test
  void shouldFormatEpochSecondsAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.SECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertEquals("2026-06-11", bucket.apply(1781103600000L / 1000));
  }

  @Test
  void shouldReturnNullForNullValue() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.MILLISECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertNull(bucket.apply(null));
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    String json =
        "{\"type\":\"date\",\"name\":\"created_at_day\",\"unit\":\"MILLISECOND\",\"timezone\":\"Asia/Seoul\",\"format\":\"yyyy-MM-dd\"}";

    Bucket bucket = objectMapper.readValue(json, Bucket.class);

    assertTrue(bucket instanceof Bucket.Date);
    assertEquals("created_at_day", bucket.getName());
    assertEquals("2026-06-11", bucket.apply(1781103600000L));
  }
}
