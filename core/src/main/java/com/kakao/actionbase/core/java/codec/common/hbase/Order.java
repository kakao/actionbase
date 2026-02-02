package com.kakao.actionbase.core.java.codec.common.hbase;

public enum Order {
  ASC {
    public int cmp(int cmp) {
      return cmp;
    }

    public byte apply(byte val) {
      return val;
    }

    public void apply(byte[] val) {}

    public void apply(byte[] val, int offset, int length) {}
  },
  DESC {
    private static final byte MASK = -1;

    public int cmp(int cmp) {
      return -1 * cmp;
    }

    public byte apply(byte val) {
      return (byte) (~val);
    }

    public void apply(byte[] val) {
      for (int i = 0; i < val.length; ++i) {
        val[i] = (byte) (~val[i]);
      }
    }

    public void apply(byte[] val, int offset, int length) {
      for (int i = 0; i < length; ++i) {
        val[offset + i] = (byte) (~val[offset + i]);
      }
    }
  };

  Order() {}

  public abstract int cmp(int var1);

  public abstract byte apply(byte var1);

  public abstract void apply(byte[] var1);

  public abstract void apply(byte[] var1, int var2, int var3);
}
