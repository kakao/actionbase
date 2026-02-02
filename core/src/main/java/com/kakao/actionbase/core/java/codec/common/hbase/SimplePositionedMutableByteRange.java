package com.kakao.actionbase.core.java.codec.common.hbase;

public class SimplePositionedMutableByteRange extends AbstractPositionedByteRange {

  public SimplePositionedMutableByteRange() {}

  public SimplePositionedMutableByteRange(int capacity) {
    this(new byte[capacity]);
  }

  public SimplePositionedMutableByteRange(byte[] bytes) {
    this.set(bytes);
  }

  public SimplePositionedMutableByteRange(byte[] bytes, int offset, int length) {
    this.set(bytes, offset, length);
  }

  public PositionedByteRange unset() {
    this.position = 0;
    this.clearHashCache();
    this.bytes = null;
    this.offset = 0;
    this.length = 0;
    return this;
  }

  public void reset() {
    this.position = 0;
    this.clearHashCache();
  }

  public PositionedByteRange set(int capacity) {
    this.position = 0;
    super.set(capacity);
    this.limit = capacity;
    return this;
  }

  public PositionedByteRange set(byte[] bytes) {
    this.position = 0;
    super.set(bytes);
    this.limit = bytes.length;
    return this;
  }

  public PositionedByteRange set(byte[] bytes, int offset, int length) {
    this.position = 0;
    super.set(bytes, offset, length);
    this.limit = length;
    return this;
  }

  public PositionedByteRange setOffset(int offset) {
    this.position = 0;
    super.setOffset(offset);
    return this;
  }

  public PositionedByteRange setLength(int length) {
    this.position = Math.min(this.position, length);
    super.setLength(length);
    return this;
  }

  public PositionedByteRange put(byte val) {
    this.put(this.position++, val);
    return this;
  }

  public PositionedByteRange put(byte[] val) {
    return 0 == val.length ? this : this.put(val, 0, val.length);
  }

  public PositionedByteRange put(byte[] val, int offset, int length) {
    if (0 == length) {
      return this;
    } else {
      this.put(this.position, val, offset, length);
      this.position += length;
      return this;
    }
  }

  public PositionedByteRange get(int index, byte[] dst) {
    super.get(index, dst);
    return this;
  }

  public PositionedByteRange get(int index, byte[] dst, int offset, int length) {
    super.get(index, dst, offset, length);
    return this;
  }

  public PositionedByteRange put(int index, byte val) {
    this.bytes[this.offset + index] = val;
    return this;
  }

  public PositionedByteRange put(int index, byte[] val) {
    return 0 == val.length ? this : this.put(index, val, 0, val.length);
  }

  public PositionedByteRange put(int index, byte[] val, int offset, int length) {
    if (0 == length) {
      return this;
    } else {
      System.arraycopy(val, offset, this.bytes, this.offset + index, length);
      return this;
    }
  }

  public PositionedByteRange putShort(short val) {
    this.putShort(this.position, val);
    this.position += 2;
    return this;
  }

  public PositionedByteRange putInt(int val) {
    this.putInt(this.position, val);
    this.position += 4;
    return this;
  }

  public PositionedByteRange putLong(long val) {
    this.putLong(this.position, val);
    this.position += 8;
    return this;
  }

  public PositionedByteRange putShort(int index, short val) {
    this.bytes[this.offset + index + 1] = (byte) val;
    val = (short) (val >> 8);
    this.bytes[this.offset + index] = (byte) val;
    this.clearHashCache();
    return this;
  }

  public PositionedByteRange putInt(int index, int val) {
    for (int i = 3; i > 0; --i) {
      this.bytes[this.offset + index + i] = (byte) val;
      val >>>= 8;
    }

    this.bytes[this.offset + index] = (byte) val;
    this.clearHashCache();
    return this;
  }

  public PositionedByteRange putLong(int index, long val) {
    for (int i = 7; i > 0; --i) {
      this.bytes[this.offset + index + i] = (byte) ((int) val);
      val >>>= 8;
    }

    this.bytes[this.offset + index] = (byte) ((int) val);
    this.clearHashCache();
    return this;
  }

  public byte[] toByteArray() {
    flip();
    byte[] bytes = new byte[getRemaining()];
    System.arraycopy(this.bytes, offset, bytes, 0, bytes.length);
    return bytes;
  }
}
