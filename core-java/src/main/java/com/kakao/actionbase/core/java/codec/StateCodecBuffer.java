package com.kakao.actionbase.core.java.codec;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.codec.common.hbase.OrderedBytes;
import com.kakao.actionbase.core.java.codec.common.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.core.java.codec.common.hbase.ValueUtils;
import com.kakao.actionbase.core.java.metadata.v3.common.Index;
import com.kakao.actionbase.core.java.metadata.v3.common.IndexField;
import com.kakao.actionbase.core.java.metadata.v3.common.SystemProperties;

import java.util.Map;

public class StateCodecBuffer {

  public SimplePositionedMutableByteRange buffer;

  public StateCodecBuffer(int capacity) {
    this.buffer = new SimplePositionedMutableByteRange(capacity);
  }

  public void plusOne() {
    ByteRangeFunctions.plusOne(buffer);
  }

  public void minusOne() {
    ByteRangeFunctions.minusOne(buffer);
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
    for (IndexField indexField : index.fields()) {
      SystemProperties systemProperty = SystemProperties.getOrNull(indexField.field());
      if (systemProperty == null) {
        encodeAny(properties.get(indexField.field()), indexField.order());
      } else {
        switch (systemProperty) {
          case VERSION:
            encodeAny(ts, indexField.order());
            break;
          case SOURCE:
            encodeAny(src, indexField.order());
            break;
          case TARGET:
            encodeAny(tgt, indexField.order());
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
