package com.kakao.actionbase.core.java.codec.common.hbase;

public abstract class AbstractByteRange implements ByteRange {
  public static final int UNSET_HASH_VALUE = -1;
  protected byte[] bytes;
  protected int offset;
  protected int length;
  protected int hash = -1;

  public AbstractByteRange() {}

  public static boolean isEmpty(ByteRange range) {
    return range == null || range.getLength() == 0;
  }

  public byte[] getBytes() {
    return this.bytes;
  }

  public abstract ByteRange unset();

  public ByteRange set(int capacity) {
    return this.set(new byte[capacity]);
  }

  public ByteRange set(byte[] bytes) {
    if (null == bytes) {
      return this.unset();
    } else {
      this.clearHashCache();
      this.bytes = bytes;
      this.offset = 0;
      this.length = bytes.length;
      return this;
    }
  }

  public ByteRange set(byte[] bytes, int offset, int length) {
    if (null == bytes) {
      return this.unset();
    } else {
      this.clearHashCache();
      this.bytes = bytes;
      this.offset = offset;
      this.length = length;
      return this;
    }
  }

  public int getOffset() {
    return this.offset;
  }

  public ByteRange setOffset(int offset) {
    this.clearHashCache();
    this.offset = offset;
    return this;
  }

  public int getLength() {
    return this.length;
  }

  public ByteRange setLength(int length) {
    this.clearHashCache();
    this.length = length;
    return this;
  }

  public boolean isEmpty() {
    return isEmpty(this);
  }

  public byte get(int index) {
    return this.bytes[this.offset + index];
  }

  public ByteRange get(int index, byte[] dst) {
    return 0 == dst.length ? this : this.get(index, dst, 0, dst.length);
  }

  public ByteRange get(int index, byte[] dst, int offset, int length) {
    if (0 == length) {
      return this;
    } else {
      System.arraycopy(this.bytes, this.offset + index, dst, offset, length);
      return this;
    }
  }

  public short getShort(int index) {
    int offset = this.offset + index;
    short n = 0;
    n = (short) (n ^ this.bytes[offset] & 255);
    n = (short) (n << 8);
    n = (short) (n ^ this.bytes[offset + 1] & 255);
    return n;
  }

  public int getInt(int index) {
    int offset = this.offset + index;
    int n = 0;

    for (int i = offset; i < offset + 4; ++i) {
      n <<= 8;
      n ^= this.bytes[i] & 255;
    }

    return n;
  }

  public long getLong(int index) {
    int offset = this.offset + index;
    long l = 0L;

    for (int i = offset; i < offset + 8; ++i) {
      l <<= 8;
      l ^= this.bytes[i] & 255;
    }

    return l;
  }

  public abstract ByteRange put(int var1, byte var2);

  public abstract ByteRange put(int var1, byte[] var2);

  public abstract ByteRange put(int var1, byte[] var2, int var3, int var4);

  public abstract ByteRange putInt(int var1, int var2);

  public abstract ByteRange putLong(int var1, long var2);

  public abstract ByteRange putShort(int var1, short var2);

  public int hashCode() {
    if (this.isHashCached()) {
      return this.hash;
    } else if (this.isEmpty()) {
      this.hash = 0;
      return this.hash;
    } else {
      int off = this.offset;
      this.hash = 0;

      for (int i = 0; i < this.length; ++i) {
        this.hash = 31 * this.hash + this.bytes[off++];
      }

      return this.hash;
    }
  }

  protected boolean isHashCached() {
    return this.hash != -1;
  }

  protected void clearHashCache() {
    this.hash = -1;
  }
}
