package com.kakao.actionbase.v2.core.metadata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BucketTests {

  ObjectMapper objectMapper = new ObjectMapper();

  // 2026-06-11T00:00:00+09:00, expressed in each ValueUnit's granularity.
  private static final long EPOCH_SECONDS = 1781103600L;
  private static final long EPOCH_MILLIS = EPOCH_SECONDS * 1_000L;
  private static final long EPOCH_MICROS = EPOCH_SECONDS * 1_000_000L;
  private static final long EPOCH_NANOS = EPOCH_SECONDS * 1_000_000_000L;

  @Test
  void shouldFormatEpochSecondsAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.SECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertEquals("2026-06-11", bucket.apply(EPOCH_SECONDS));
  }

  @Test
  void shouldFormatEpochMillisAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.MILLISECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertEquals("2026-06-11", bucket.apply(EPOCH_MILLIS));
  }

  @Test
  void shouldFormatEpochMicrosAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.MICROSECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertEquals("2026-06-11", bucket.apply(EPOCH_MICROS));
  }

  @Test
  void shouldFormatEpochNanosAsDateString() {
    Bucket.Date bucket =
        new Bucket.Date("created_at_day", Bucket.ValueUnit.NANOSECOND, "Asia/Seoul", "yyyy-MM-dd");

    assertEquals("2026-06-11", bucket.apply(EPOCH_NANOS));
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
    assertEquals("2026-06-11", bucket.apply(EPOCH_MILLIS));
  }
}
