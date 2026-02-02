package com.kakao.actionbase.v2.engine.compat;

import java.util.Map;

public class JavaKotlinTypeCompat {

  private JavaKotlinTypeCompat() {}

  // Legacy Kotlin code declares Map<String, Any> but actually expects nullable values.
  // This bridges Java's Object (nullable) with Kotlin's Any (non-null) for compatibility.
  public static Map<String, Object> wrap(Map<String, Object> input) {
    return input;
  }
}
