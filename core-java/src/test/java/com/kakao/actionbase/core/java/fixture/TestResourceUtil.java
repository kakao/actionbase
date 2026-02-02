package com.kakao.actionbase.core.java.fixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class TestResourceUtil {

  private TestResourceUtil() {}

  public static String load(String resourcePath) {
    try {
      return new String(
          Files.readAllBytes(
              Paths.get(TestResourceUtil.class.getClassLoader().getResource(resourcePath).toURI())),
          StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load resource: " + resourcePath, e);
    }
  }
}
