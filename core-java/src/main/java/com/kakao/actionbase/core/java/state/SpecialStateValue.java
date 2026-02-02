package com.kakao.actionbase.core.java.state;

public enum SpecialStateValue {
  DELETED("__DELETED__"),
  UNSET("__UNSET__");

  final String stateValue;

  SpecialStateValue(String code) {
    this.stateValue = code;
  }

  public String code() {
    return stateValue;
  }

  public static boolean isSpecialStateValue(Object code) {
    if (code instanceof String) {
      return code.equals(DELETED.code()) || code.equals(UNSET.code());
    } else {
      return false;
    }
  }

  public static SpecialStateValue getSpecialStateValue(String code) {
    if (DELETED.code().equals(code)) {
      return DELETED;
    } else if (UNSET.code().equals(code)) {
      return UNSET;
    } else {
      throw new IllegalArgumentException("Invalid special state value: " + code);
    }
  }
}
