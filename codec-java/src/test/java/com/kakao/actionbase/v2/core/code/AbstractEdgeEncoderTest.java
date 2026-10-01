package com.kakao.actionbase.v2.core.code;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AbstractEdgeEncoderTest {

  @Test
  void shouldReturnBorrowedBufferAfterEncodingFails() {
    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    BytesKeyValueEdgeEncoder encoder = factory.getBytesKeyValueEncoder();
    EdgeBuffer borrowed = factory.pool.peek();

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                encoder.useAsByteArray(
                    buffer -> {
                      buffer.encodeInt8((byte) 7);
                      buffer.encodeAny(new Object());
                    }));

    assertEquals("Unexpected data of type : java.lang.Object", failure.getMessage());
    assertEquals(1, encoder.getPoolSize());
    assertSame(borrowed, factory.pool.peek());

    byte[] encoded = encoder.useAsByteArray(buffer -> buffer.encodeInt8((byte) 1));
    byte[] expected =
        new EdgeEncoderFactory()
            .getBytesKeyValueEncoder()
            .useAsByteArray(buffer -> buffer.encodeInt8((byte) 1));
    assertArrayEquals(expected, encoded);
    assertSame(borrowed, factory.pool.peek());
  }
}
