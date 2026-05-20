package com.kakao.actionbase.pipeline.runner;

/**
 * Test sentinel whose static initializer throws. Used to assert {@link ClassResolver} returns the
 * class metadata without triggering {@code <clinit>}, and that {@code StepsBuilder} rejects
 * non-Step FQNs before any initializer runs. Written in Java so the static initializer block is
 * unambiguous (Scala objects emit their own MODULE$ init which makes "did init run?" harder to
 * observe from outside).
 */
public final class ThrowingClinitSentinel {
  static {
    if (Boolean.parseBoolean("true")) {
      throw new RuntimeException("ThrowingClinitSentinel.<clinit> must not run in this test");
    }
  }

  private ThrowingClinitSentinel() {}
}
