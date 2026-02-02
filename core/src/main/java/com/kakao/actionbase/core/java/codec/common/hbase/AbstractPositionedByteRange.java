package com.kakao.actionbase.core.java.codec.common.hbase;

public abstract class AbstractPositionedByteRange extends AbstractByteRange
    implements PositionedByteRange {
  protected int position = 0;
  protected int limit = 0;

  public AbstractPositionedByteRange() {}

  public abstract PositionedByteRange unset();

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

  public int getPosition() {
    return this.position;
  }

  public PositionedByteRange setPosition(int position) {
    this.position = position;
    return this;
  }

  public void skip(int length) {
    this.position += length;
  }

  public int getRemaining() {
    return this.length - this.position;
  }

  public byte peek() {
    return this.bytes[this.offset + this.position];
  }

  public byte get() {
    return this.get(this.position++);
  }

  public PositionedByteRange get(byte[] dst) {
    return 0 == dst.length ? this : this.get(dst, 0, dst.length);
  }

  public PositionedByteRange get(byte[] dst, int offset, int length) {
    if (0 == length) {
      return this;
    } else {
      super.get(this.position, dst, offset, length);
      this.position += length;
      return this;
    }
  }

  public abstract PositionedByteRange put(byte var1);

  public abstract PositionedByteRange put(byte[] var1);

  public abstract PositionedByteRange put(byte[] var1, int var2, int var3);

  public abstract PositionedByteRange putInt(int var1, int var2);

  public abstract PositionedByteRange putLong(int var1, long var2);

  public abstract PositionedByteRange putShort(int var1, short var2);

  public abstract PositionedByteRange putInt(int var1);

  public abstract PositionedByteRange putLong(long var1);

  public abstract PositionedByteRange putShort(short var1);

  PositionedByteRange flip() {
    this.clearHashCache();
    this.length = this.position;
    this.position = this.offset;
    return this;
  }

  PositionedByteRange clear() {
    this.clearHashCache();
    this.position = 0;
    this.length = this.bytes.length - this.offset;
    return this;
  }

  public PositionedByteRange get(int index, byte[] dst) {
    super.get(index, dst);
    return this;
  }

  public PositionedByteRange get(int index, byte[] dst, int offset, int length) {
    super.get(index, dst, offset, length);
    return this;
  }

  public short getShort() {
    short s = this.getShort(this.position);
    this.position += 2;
    return s;
  }

  public int getInt() {
    int i = this.getInt(this.position);
    this.position += 4;
    return i;
  }

  public long getLong() {
    long l = this.getLong(this.position);
    this.position += 8;
    return l;
  }

  public abstract PositionedByteRange put(int var1, byte var2);

  public abstract PositionedByteRange put(int var1, byte[] var2);

  public abstract PositionedByteRange put(int var1, byte[] var2, int var3, int var4);

  public PositionedByteRange setLimit(int limit) {
    this.limit = limit;
    return this;
  }

  public int getLimit() {
    return this.limit;
  }
}
