package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.Order;
import com.kakao.actionbase.v2.core.code.hbase.OrderedBytes;
import com.kakao.actionbase.v2.core.code.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;
import com.kakao.actionbase.v2.core.metadata.SystemProperties;

import java.util.Map;

public class EdgeBuffer {
  static final int DEFAULT_CAPACITY = 5120;

  public SimplePositionedMutableByteRange buffer;

  public EdgeBuffer() {
    this(DEFAULT_CAPACITY);
  }

  public EdgeBuffer(int capacity) {
    this.buffer = new SimplePositionedMutableByteRange(capacity);
  }

  public EdgeBuffer(SimplePositionedMutableByteRange underlying) {
    this.buffer = underlying;
  }

  public void plusOne() {
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

  public void minusOne() {
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

  public void encodeWithSalt(Object value) {
    ValueUtils.serializeWithSalt(buffer, value);
  }

  public void encodeWithHash(Object value, int id, byte type) {
    ValueUtils.serializeWithHash(buffer, value, id, type);
  }

  public void encodeInt64(long value) {
    OrderedBytes.encodeInt64(buffer, value, Order.ASC);
  }

  public void encodeInt32(int value) {
    OrderedBytes.encodeInt32(buffer, value, Order.ASC);
  }

  public void encodeInt8(byte value) {
    OrderedBytes.encodeInt8(buffer, value, Order.ASC);
  }

  public void encodeString(String value) {
    OrderedBytes.encodeString(buffer, value, Order.ASC);
  }

  public void encodeAny(Object value) {
    ValueUtils.serialize(buffer, value);
  }

  public void encodeAny(Object value, Order order) {
    ValueUtils.serialize(buffer, value, order);
  }

  public void encodeMap(Map<String, Object> value) {
    if (value == null) return;
    for (Map.Entry<String, Object> e : value.entrySet()) {
      encodeString(e.getKey());
      encodeAny(e.getValue());
    }
  }

  public void encodeFields(
      Index index, long ts, Object src, Object tgt, Map<String, Object> properties) {
    for (Index.Field field : index.fields) {
      SystemProperties systemProperty = SystemProperties.getOrNull(field.name);
      if (systemProperty == null) {
        encodeAny(properties.get(field.name), field.order);
      } else {
        switch (systemProperty) {
          case TS:
            encodeAny(ts, field.order);
            break;
          case SRC:
            encodeAny(src, field.order);
            break;
          case TGT:
            encodeAny(tgt, field.order);
            break;
        }
      }
    }
  }

  public void reset() {
    buffer.reset();
  }

  public void put(byte[] val, int offset, int length) {
    buffer.put(val, offset, length);
  }
}
