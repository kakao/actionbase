package com.kakao.actionbase.v2.core.code;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AbstractEdgeEncoderTest {

  @Test
  void shouldEncodeWhenPoolIsExhaustedByConcurrentBorrow() throws Exception {
    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    BytesKeyValueEdgeEncoder encoder = factory.getBytesKeyValueEncoder();
    CountDownLatch firstBorrowed = new CountDownLatch(1);
    Semaphore releaseFirst = new Semaphore(0);
    AtomicReference<EdgeBuffer> firstBuffer = new AtomicReference<>();
    AtomicReference<EdgeBuffer> secondBuffer = new AtomicReference<>();
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<byte[]> first =
          executor.submit(
              () ->
                  encoder.useAsByteArray(
                      buffer -> {
                        firstBuffer.set(buffer);
                        buffer.encodeInt8((byte) 1);
                        firstBorrowed.countDown();
                        releaseFirst.acquireUninterruptibly();
                      }));

      assertTrue(firstBorrowed.await(10, TimeUnit.SECONDS));
      Future<byte[]> second =
          executor.submit(
              () ->
                  encoder.useAsByteArray(
                      buffer -> {
                        secondBuffer.set(buffer);
                        buffer.encodeInt8((byte) 2);
                      }));

      byte[] secondBytes = second.get(10, TimeUnit.SECONDS);
      assertNotSame(firstBuffer.get(), secondBuffer.get());
      releaseFirst.release();
      byte[] firstBytes = first.get(10, TimeUnit.SECONDS);

      BytesKeyValueEdgeEncoder unpooled = new EdgeEncoderFactory().getBytesKeyValueEncoder();
      assertArrayEquals(unpooled.useAsByteArray(buffer -> buffer.encodeInt8((byte) 1)), firstBytes);
      assertArrayEquals(
          unpooled.useAsByteArray(buffer -> buffer.encodeInt8((byte) 2)), secondBytes);
    } finally {
      releaseFirst.release();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

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
