package com.kakao.actionbase.core.java.util;

import java.nio.charset.StandardCharsets;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

public class HashUtils {

  private HashUtils() {}

  private static final int seed = 0;

  private static final XXHash32 xxhash32 = XXHashFactory.fastestInstance().hash32();

  public static int stringHash(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    return xxhash32.hash(bytes, 0, bytes.length, seed);
  }
}
