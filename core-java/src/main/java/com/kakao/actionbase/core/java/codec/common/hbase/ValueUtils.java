package com.kakao.actionbase.core.java.codec.common.hbase;

import static com.kakao.actionbase.core.java.codec.common.hbase.OrderedBytes.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

public final class ValueUtils {

  public static final int DEFAULT_NUM_BUCKETS = 256;
  static int seed = 0;
  static XXHash32 xxhash32 = XXHashFactory.fastestInstance().hash32();

  public static ValueType getValueType(Object o) {
    if (o == null) {
      return ValueType.NULL;
    } else if (o instanceof Boolean) {
      return ValueType.BOOLEAN;
    } else if (o instanceof String) {
      return ValueType.STRING;
    } else if (o instanceof Byte) {
      return ValueType.BYTE;
    } else if (o instanceof Short) {
      return ValueType.SHORT;
    } else if (o instanceof Integer) {
      return ValueType.INT;
    } else if (o instanceof Long) {
      return ValueType.LONG;
    } else if (o instanceof Float) {
      return ValueType.FLOAT;
    } else if (o instanceof Double) {
      return ValueType.DOUBLE;
    } else if (o instanceof BigDecimal) {
      return ValueType.DECIMAL;
    } else if (o instanceof JsonNode) {
      return ValueType.JSON;
    } else if (o instanceof LocalDate) {
      return ValueType.DATE;
    } else if (o instanceof LocalTime) {
      return ValueType.TIME;
    } else if (o instanceof LocalDateTime) {
      return ValueType.TIMESTAMP;
    } else if (o instanceof Duration) {
      return ValueType.INTERVAL;
    } else if (o instanceof byte[]) {
      return ValueType.BINARY;
    } else if (o instanceof Enum) {
      return ValueType.ENUM;
    } else if (o instanceof UUID) {
      return ValueType.UUID;
    } else if (o instanceof Serializable) {
      return ValueType.SERIALIZABLE;
    } else {
      throw new IllegalArgumentException("Unexpected data of type : " + o.getClass().getName());
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserialize(PositionedByteRange buffer) {
    byte type = buffer.peek();
    switch (type) {
      case NULL_HEADER:
      case NULL_HEADER_DESC:
        buffer.get();
        return null;
      case STRING_HEADER:
      case STRING_HEADER_DESC:
        return (T) decodeString(buffer);
      case TRUE_HEADER:
      case TRUE_HEADER_DESC:
        buffer.get();
        return (T) Boolean.TRUE;
      case FALSE_HEADER:
      case FALSE_HEADER_DESC:
        buffer.get();
        return (T) Boolean.FALSE;
      case INT8_HEADER:
      case INT8_HEADER_DESC:
        return (T) Byte.valueOf(decodeInt8(buffer));
      case INT16_HEADER:
      case INT16_HEADER_DESC:
        return (T) Short.valueOf(decodeInt16(buffer));
      case INT32_HEADER:
      case INT32_HEADER_DESC:
        return (T) Integer.valueOf(decodeInt32(buffer));
      case INT64_HEADER:
      case INT64_HEADER_DESC:
        return (T) Long.valueOf(decodeInt64(buffer));
      case FLOAT32_HEADER:
      case FLOAT32_HEADER_DESC:
        return (T) Float.valueOf(decodeFloat32(buffer));
      case FLOAT64_HEADER:
      case FLOAT64_HEADER_DESC:
        return (T) Double.valueOf(decodeFloat64(buffer));
      case JSON_HEADER:
      case JSON_HEADER_DESC:
        return (T) decodeJsonNode(buffer);
      default:
        throw new IllegalArgumentException("Unexpected data type: " + type);
    }
  }

  public static void serialize(PositionedByteRange buffer, Object o) {
    serialize(buffer, o, Order.ASC);
  }

  public static void serialize(PositionedByteRange buffer, Object o, Order order) {
    ValueType type = getValueType(o);
    switch (type) {
      case NULL:
        encodeNull(buffer, order);
        break;
      case STRING:
        encodeString(buffer, (String) o, order);
        break;
      case BOOLEAN:
        encodeBoolean(buffer, (Boolean) o, order);
        break;
      case BYTE:
        encodeInt8(buffer, (Byte) o, order);
        break;
      case SHORT:
        encodeInt16(buffer, (Short) o, order);
        break;
      case INT:
        encodeInt32(buffer, (Integer) o, order);
        break;
      case LONG:
        encodeInt64(buffer, (Long) o, order);
        break;
      case FLOAT:
        encodeFloat32(buffer, (Float) o, order);
        break;
      case DOUBLE:
        encodeFloat64(buffer, (Double) o, order);
        break;
      case JSON:
        encodeJsonNode(buffer, (JsonNode) o, order);
        break;
      default:
        throw new IllegalArgumentException("Unexpected data of type : " + o.getClass().getName());
    }
  }

  public static int stringHash(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    return xxhash32.hash(bytes, 0, bytes.length, seed);
  }

  public static void serializeWithHash(PositionedByteRange buffer, Object o, int id, byte type) {
    int saltPosition = buffer.getPosition();
    buffer.putInt(0); // reserve for salt
    int valuePosition = buffer.getPosition();
    serialize(buffer, o);
    encodeInt32(buffer, id, Order.ASC);
    encodeInt8(buffer, type, Order.ASC);

    int valueLength = buffer.getPosition() - valuePosition;

    int salt = xxhash32.hash(buffer.getBytes(), valuePosition, valueLength, seed);
    buffer.putInt(saltPosition, salt);
  }

  public static void serializeWithSalt(PositionedByteRange buffer, Object o) {
    int saltPosition = buffer.getPosition();
    buffer.put((byte) 0); // reserve for salt
    int valuePosition = buffer.getPosition();
    serialize(buffer, o);
    int valueLength = buffer.getPosition() - valuePosition;
    byte salt = getSaltingByte(buffer.getBytes(), valuePosition, valueLength);
    buffer.put(saltPosition, salt);
  }

  /**
   * Returns the salt for a given value.
   *
   * @param value the value
   * @return the salt to prepend to {@code value}
   */
  public static byte getSaltingByte(byte[] value, int offset, int length) {
    int hash = calculateHashCode(value, offset, length);
    return (byte) (Math.abs(hash) % DEFAULT_NUM_BUCKETS);
  }

  private static int calculateHashCode(byte[] a, int offset, int length) {
    if (a == null) return 0;
    int result = 1;
    for (int i = offset; i < offset + length; ++i) {
      result = 31 * result + a[i];
    }
    return result;
  }
}
