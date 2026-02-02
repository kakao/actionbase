package com.kakao.actionbase.core.java.metadata.v3.common;

import java.util.HashMap;
import java.util.Map;

public enum SystemProperties {
  VERSION("version", -1),
  SOURCE("source", -2),
  TARGET("target", -3);

  private static final Map<String, SystemProperties> caseSensitiveMap = new HashMap<>();
  private static final Map<Integer, SystemProperties> indexMap = new HashMap<>();

  static {
    for (SystemProperties systemProperties : SystemProperties.values()) {
      caseSensitiveMap.put(systemProperties.str, systemProperties);
      indexMap.put(systemProperties.index, systemProperties);
    }
  }

  private final String str;
  private final int index;

  SystemProperties(String str, int index) {
    this.str = str;
    this.index = index;
  }

  public static Integer indexOf(String str) {
    SystemProperties systemProperties = caseSensitiveMap.get(str);
    if (systemProperties == null) {
      return null;
    } else {
      return systemProperties.index;
    }
  }

  public static SystemProperties getOrNull(int index) {
    return indexMap.get(index);
  }

  public static SystemProperties getOrNull(String name) {
    return caseSensitiveMap.get(name);
  }

  public static boolean isSystemProperty(String str) {
    return caseSensitiveMap.containsKey(str);
  }

  public String getStr() {
    return str;
  }

  public int getIndex() {
    return index;
  }
}
