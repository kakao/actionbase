package com.kakao.actionbase.core.java.state.base;

import com.kakao.actionbase.core.java.util.ULID;

import java.util.Random;

public class EventCompanion {

  private EventCompanion() {}

  private static final Random random = new Random();

  public static String generateRandomId() {
    return ULID.random(random);
  }
}
