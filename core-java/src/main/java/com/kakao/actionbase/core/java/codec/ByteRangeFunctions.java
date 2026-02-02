package com.kakao.actionbase.core.java.codec;

import com.kakao.actionbase.core.java.codec.common.hbase.SimplePositionedMutableByteRange;

public class ByteRangeFunctions {

  private ByteRangeFunctions() {}

  public static void plusOne(SimplePositionedMutableByteRange buffer) {
    int position = buffer.getPosition();
    byte[] bytes = buffer.getBytes();

    boolean carry = true;
    for (int i = position - 1; i >= 0 && carry; i--) {
      if ((bytes[i] & 0xFF) == 0xFF) {
        bytes[i] = 0;
      } else {
        bytes[i]++;
        carry = false;
      }
    }
    if (carry) {
      throw new IllegalArgumentException("Overflow");
    }
  }

  public static void minusOne(SimplePositionedMutableByteRange buffer) {
    int position = buffer.getPosition();
    byte[] bytes = buffer.getBytes();

    boolean borrow = true;
    for (int i = position - 1; i >= 0 && borrow; i--) {
      if (bytes[i] == 0x00) {
        bytes[i] = (byte) 0xFF;
      } else {
        bytes[i]--;
        borrow = false;
      }
    }
    if (borrow) {
      throw new IllegalArgumentException("Underflow");
    }
  }
}
