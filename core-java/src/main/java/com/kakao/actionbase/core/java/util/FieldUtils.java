package com.kakao.actionbase.core.java.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FieldUtils {

  private FieldUtils() {}

  public static <T> Object[] toFieldArray(
      List<T> items, java.util.function.Function<T, Object> mapper) {
    Object[] result = new Object[items.size()];
    for (int i = 0; i < items.size(); i++) {
      result[i] = mapper.apply(items.get(i));
    }
    return result;
  }

  public static <T, R> List<R> fromFieldArray(
      Object items, Function<Map<String, Object>, R> mapper) {
    if (items == null) {
      return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
    List<R> result = new ArrayList<>(itemsList.size());

    for (Map<String, Object> item : itemsList) {
      result.add(mapper.apply(item));
    }

    return result;
  }

  public static <K, V> Map<K, V> mapOf() {
    return Collections.emptyMap();
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1) {
    return Collections.singletonMap(k1, v1);
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
    Map<K, V> map = new HashMap<>(2, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
    Map<K, V> map = new HashMap<>(3, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    Map<K, V> map = new HashMap<>(4, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    Map<K, V> map = new HashMap<>(5, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    Map<K, V> map = new HashMap<>(6, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    map.put(k6, v6);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    Map<K, V> map = new HashMap<>(7, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    map.put(k6, v6);
    map.put(k7, v7);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8) {
    Map<K, V> map = new HashMap<>(8, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    map.put(k6, v6);
    map.put(k7, v7);
    map.put(k8, v8);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8,
      K k9,
      V v9) {
    Map<K, V> map = new HashMap<>(9, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    map.put(k6, v6);
    map.put(k7, v7);
    map.put(k8, v8);
    map.put(k9, v9);
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map<K, V> mapOf(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8,
      K k9,
      V v9,
      K k10,
      V v10) {
    Map<K, V> map = new HashMap<>(10, 1.0f);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    map.put(k5, v5);
    map.put(k6, v6);
    map.put(k7, v7);
    map.put(k8, v8);
    map.put(k9, v9);
    map.put(k10, v10);
    return Collections.unmodifiableMap(map);
  }
}
