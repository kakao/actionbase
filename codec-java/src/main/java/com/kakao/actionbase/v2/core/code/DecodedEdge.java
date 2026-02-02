package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.OrderedBytes;
import com.kakao.actionbase.v2.core.code.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class DecodedEdge {

  public static byte[] empty = new byte[0];
  private final boolean active;
  private final long ts;
  private final Object src;
  private final Object tgt;
  private final Map<String, VersionValue> propertyAsMap;
  private final Direction direction;
  private final int labelId;
  private final EncodedEdgeType type;

  DecodedEdge(
      boolean active,
      long ts,
      Object src,
      Object tgt,
      Map<String, VersionValue> propertyAsMap,
      Direction direction,
      int labelId,
      EncodedEdgeType type) {
    this.active = active;
    this.ts = ts;
    this.src = src;
    this.tgt = tgt;
    this.propertyAsMap = propertyAsMap;
    this.direction = direction;
    this.labelId = labelId;
    this.type = type;
  }

  public static DecodedEdge fromMetastore(
      KeyValue<String> encodedValue, Map<Integer, String> hashToFieldNameMap) {
    String[] sp = encodedValue.key.split(":", 2);
    KeyFieldValue<String> encodedValue1 = new KeyFieldValue<>(sp[0], sp[1], encodedValue.value);
    return fromString(encodedValue1, hashToFieldNameMap);
  }

  public static DecodedEdge fromString(
      KeyFieldValue<String> encodedValue, Map<Integer, String> hashToFieldNameMap) {
    byte[] key = SerializationUtils.base64.decode(encodedValue.key);
    byte[] field = SerializationUtils.hex.decode(encodedValue.field);
    byte[] value = SerializationUtils.base64.decode(encodedValue.value);
    KeyFieldValue<byte[]> encodedValue1 = new KeyFieldValue<>(key, field, value);
    return from(encodedValue1, hashToFieldNameMap);
  }

  public static DecodedEdge from(
      KeyFieldValue<byte[]> encodedValue, Map<Integer, String> hashToFieldNameMap) {
    SimplePositionedMutableByteRange k = new SimplePositionedMutableByteRange(encodedValue.key);
    SimplePositionedMutableByteRange f;
    if (encodedValue.field == null) {
      f = new SimplePositionedMutableByteRange(empty);
    } else {
      f = new SimplePositionedMutableByteRange(encodedValue.field);
    }
    SimplePositionedMutableByteRange v = new SimplePositionedMutableByteRange(encodedValue.value);

    k.getInt(); // skip xxhash32
    Object src = ValueUtils.deserialize(k);
    int labelId = OrderedBytes.decodeInt32(k);
    byte encodedEdgeTypeCode = OrderedBytes.decodeInt8(k);
    EncodedEdgeType encodedEdgeType = EncodedEdgeType.of(encodedEdgeTypeCode);

    Object tgt;
    Direction direction = null;
    if (encodedEdgeType == EncodedEdgeType.HASH_EDGE_TYPE) {
      if (k.getRemaining() > 0) {
        // LSMTree stores tgt in the key
        tgt = ValueUtils.deserialize(k);
      } else {
        // Redicoke stores tgt in the field
        if (f.getRemaining() > 0) {
          tgt = ValueUtils.deserialize(f);
        } else {
          throw new IllegalArgumentException("Invalid encodedValue: " + encodedValue);
        }
      }
    } else if (encodedEdgeType == EncodedEdgeType.LOCK_EDGE_TYPE) {
      // All storage stores tgt in the key
      if (k.getRemaining() > 0) {
        tgt = ValueUtils.deserialize(k);
      } else {
        throw new IllegalArgumentException("Invalid encodedValue: " + encodedValue);
      }
    } else if (encodedEdgeType == EncodedEdgeType.INDEXED_EDGE_TYPE
        || encodedEdgeType == EncodedEdgeType.IMMUTABLE_INDEXED_EDGE_TYPE) {
      // direction, indexId, indexValues, tgt
      byte directionCode = OrderedBytes.decodeInt8(k);
      direction = Direction.of(directionCode);

      // skip index id
      OrderedBytes.decodeInt32(k);

      if (k.getRemaining() > 0) {
        // LSMTree stores tgt in the key
        // index values, the last one is tgt
        Object last = ValueUtils.deserialize(k);
        while (k.getRemaining() > 0) {
          last = ValueUtils.deserialize(k);
        }
        tgt = last;
      } else {
        // Redicoke stores tgt in the field
        if (f.getRemaining() > 0) {
          // index values, the last one is tgt
          Object last = ValueUtils.deserialize(f);
          while (f.getRemaining() > 0) {
            last = ValueUtils.deserialize(f);
          }
          tgt = last;
        } else {
          throw new IllegalArgumentException("Invalid encodedValue: " + encodedValue);
        }
      }
    } else {
      throw new IllegalArgumentException("Unknown encodedEdgeType: " + encodedEdgeType);
    }

    Map<String, VersionValue> propertyAsMap = new HashMap<>();
    if (encodedEdgeType == EncodedEdgeType.HASH_EDGE_TYPE) {
      HashEdgeValue hashEdgeValue =
          AbstractEdgeEncoder.decodeHashEdgeValueFromBytes(encodedValue.value, hashToFieldNameMap);
      propertyAsMap = hashEdgeValue.getMap();
      long ts = hashEdgeValue.getTs();
      return new DecodedEdge(
          hashEdgeValue.active.isActive(),
          ts,
          src,
          tgt,
          propertyAsMap,
          null,
          labelId,
          encodedEdgeType);
    } else if (encodedEdgeType == EncodedEdgeType.LOCK_EDGE_TYPE) {
      long ts = OrderedBytes.decodeInt64(v);
      return new DecodedEdge(true, ts, src, tgt, propertyAsMap, null, labelId, encodedEdgeType);
    } else if (encodedEdgeType == EncodedEdgeType.INDEXED_EDGE_TYPE) {
      long ts = OrderedBytes.decodeInt64(v);
      while (v.getRemaining() > 0) {
        int propertyKeyHash = OrderedBytes.decodeInt32(v);
        String propertyKey = hashToFieldNameMap.get(propertyKeyHash);
        if (propertyKey == null) {
          propertyKey = String.valueOf(propertyKeyHash);
        }
        Object propertyValue = ValueUtils.deserialize(v);
        propertyAsMap.put(propertyKey, VersionValue.noVersionValue(propertyValue));
      }
      return new DecodedEdge(
          true, ts, src, tgt, propertyAsMap, direction, labelId, encodedEdgeType);
    } else {
      // encodedEdgeType == EncodedEdgeType.IMMUTABLE_INDEXED_EDGE_TYPE
      throw new IllegalArgumentException("Unknown encodedEdgeType: " + encodedEdgeType);
    }
  }

  public static Map<String, Object> decodeV0Value(String encodedValue) {
    byte[] bytes = Base64.getDecoder().decode(encodedValue);
    SimplePositionedMutableByteRange v = new SimplePositionedMutableByteRange(bytes);

    OrderedBytes.decodeInt64(v); // skip ts
    OrderedBytes.decodeString(v); // skip tgt

    Map<String, Object> propertyAsMap = new HashMap<>();
    while (v.getRemaining() > 0) {
      String propertyKey = OrderedBytes.decodeString(v);
      Object propertyValue = ValueUtils.deserialize(v);
      propertyAsMap.put(propertyKey, propertyValue);
    }
    return propertyAsMap;
  }

  public boolean isActive() {
    return active;
  }

  public long getTs() {
    return ts;
  }

  public Object getSrc() {
    return src;
  }

  public Object getTgt() {
    return tgt;
  }

  public Map<String, VersionValue> getPropertyAsMap() {
    return propertyAsMap;
  }

  public Direction getDirection() {
    return direction;
  }

  public int getLabelId() {
    return labelId;
  }

  public EncodedEdgeType getType() {
    return type;
  }

  public String toString() {
    return "DecodedEdge(active="
        + active
        + ", ts="
        + ts
        + ", src="
        + src
        + ", tgt="
        + tgt
        + ", propertyAsMap="
        + propertyAsMap
        + ", direction="
        + direction
        + ", labelId="
        + labelId
        + ", type="
        + type
        + ")";
  }
}
