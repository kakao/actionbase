package com.kakao.actionbase.v2.core.metadata;

public enum Active {
  INACTIVE((byte) 0), // b0000
  ACTIVE((byte) 1); // b0001

  private final byte code;

  Active(byte code) {
    this.code = code;
  }

  public static Active of(byte code) {
    switch (code) {
      case 0:
      case 2: // backward compatibility (it was PENDING status)
        return INACTIVE;
      case 1:
        return ACTIVE;
      default:
        throw new IllegalArgumentException("Unknown code: " + code);
    }
  }

  public byte getCode() {
    return code;
  }

  public boolean isActive() {
    return this == ACTIVE;
  }
}
