package com.kakao.actionbase.core.java.codec.common.hbase;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OrderedBytes {
  public static final Charset UTF8 = StandardCharsets.UTF_8;

  public static final byte NULL_HEADER = 5;

  public static final byte NULL_HEADER_DESC = -6;

  public static final byte STRING_HEADER = 52;

  public static final byte STRING_HEADER_DESC = -53;

  public static final byte STRING_FOOTER = 0;

  public static final byte FALSE_HEADER = 53;

  public static final byte FALSE_HEADER_DESC = -54;

  public static final byte TRUE_HEADER = 54;

  public static final byte TRUE_HEADER_DESC = -55;

  public static final byte INT8_HEADER = 41;

  public static final byte INT8_HEADER_DESC = -42;

  public static final byte INT16_HEADER = 42;

  public static final byte INT16_HEADER_DESC = -43;

  public static final byte INT32_HEADER = 43;

  public static final byte INT32_HEADER_DESC = -44;

  public static final byte INT64_HEADER = 44;

  public static final byte INT64_HEADER_DESC = -45;

  public static final byte FLOAT32_HEADER = 48;

  public static final byte FLOAT32_HEADER_DESC = -49;

  public static final byte FLOAT64_HEADER = 49;

  public static final byte FLOAT64_HEADER_DESC = -50;

  public static final byte JSON_HEADER = 50;

  public static final byte JSON_HEADER_DESC = -51;

  public OrderedBytes() {}

  private static final ObjectMapper mapper = new ObjectMapper();

  public static int encodeNull(PositionedByteRange dst, Order ord) {
    dst.put(ord.apply(NULL_HEADER));
    return 1;
  }

  public static int encodeString(PositionedByteRange dst, String val, Order ord) {
    if (null == val) {
      return encodeNull(dst, ord);
    } else if (val.contains("\u0000")) {
      throw new IllegalArgumentException("Cannot encode String values containing '\\u0000'");
    } else {
      int offset = dst.getOffset();
      int start = dst.getPosition();
      dst.put(STRING_HEADER);
      dst.put(val.getBytes(UTF8));
      dst.put(STRING_FOOTER);
      ord.apply(dst.getBytes(), offset + start, dst.getPosition() - start);
      return dst.getPosition() - start;
    }
  }

  public static String decodeString(PositionedByteRange src) {
    byte header = src.get();

    if (header == NULL_HEADER || header == NULL_HEADER_DESC) {
      return null;
    } else {
      assert header == STRING_HEADER || header == STRING_HEADER_DESC;

      Order ord = header == STRING_HEADER ? Order.ASC : Order.DESC;
      byte[] a = src.getBytes();
      int offset = src.getOffset();
      int start = src.getPosition();
      byte terminator = ord.apply((byte) 0);
      int rawStartPos = offset + start;

      int rawTermPos;
      for (rawTermPos = rawStartPos; a[rawTermPos] != terminator; ++rawTermPos) {}

      src.setPosition(rawTermPos - offset + 1);
      if (Order.DESC == ord) {
        byte[] copy = new byte[rawTermPos - rawStartPos];
        System.arraycopy(a, rawStartPos, copy, 0, copy.length);
        ord.apply(copy);
        return new String(copy, UTF8);
      } else {
        return new String(a, rawStartPos, rawTermPos - rawStartPos, UTF8);
      }
    }
  }

  public static int encodeBoolean(PositionedByteRange dst, boolean val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    if (val) {
      dst.put(TRUE_HEADER);
    } else {
      dst.put(FALSE_HEADER);
    }
    ord.apply(dst.getBytes(), offset + start, 1);
    return 1;
  }

  public static int encodeInt8(PositionedByteRange dst, byte val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    dst.put(INT8_HEADER).put((byte) (val ^ 128));
    ord.apply(dst.getBytes(), offset + start, 2);
    return 2;
  }

  public static byte decodeInt8(PositionedByteRange src) {
    byte header = src.get();

    assert header == INT8_HEADER || header == INT8_HEADER_DESC;

    Order ord = header == INT8_HEADER ? Order.ASC : Order.DESC;
    return (byte) ((ord.apply(src.get()) ^ 128) & 255);
  }

  public static int encodeInt16(PositionedByteRange dst, short val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    dst.put(INT16_HEADER).put((byte) (val >> 8 ^ 128)).put((byte) val);
    ord.apply(dst.getBytes(), offset + start, 3);
    return 3;
  }

  public static short decodeInt16(PositionedByteRange src) {
    byte header = src.get();

    assert header == INT16_HEADER || header == INT16_HEADER_DESC;

    Order ord = header == INT16_HEADER ? Order.ASC : Order.DESC;
    short val = (short) ((ord.apply(src.get()) ^ 128) & 255);
    val = (short) ((val << 8) + (ord.apply(src.get()) & 255));
    return val;
  }

  public static int encodeInt32(PositionedByteRange dst, int val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    dst.put(INT32_HEADER)
        .put((byte) (val >> 24 ^ 128))
        .put((byte) (val >> 16))
        .put((byte) (val >> 8))
        .put((byte) val);
    ord.apply(dst.getBytes(), offset + start, 5);
    return 5;
  }

  public static int decodeInt32(PositionedByteRange src) {
    byte header = src.get();

    assert header == INT32_HEADER || header == INT32_HEADER_DESC;

    Order ord = header == INT32_HEADER ? Order.ASC : Order.DESC;
    int val = (ord.apply(src.get()) ^ 128) & 255;

    for (int i = 1; i < 4; ++i) {
      val = (val << 8) + (ord.apply(src.get()) & 255);
    }

    return val;
  }

  public static int encodeInt64(PositionedByteRange dst, long val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    dst.put(INT64_HEADER)
        .put((byte) ((int) (val >> 56 ^ 128L)))
        .put((byte) ((int) (val >> 48)))
        .put((byte) ((int) (val >> 40)))
        .put((byte) ((int) (val >> 32)))
        .put((byte) ((int) (val >> 24)))
        .put((byte) ((int) (val >> 16)))
        .put((byte) ((int) (val >> 8)))
        .put((byte) ((int) val));
    ord.apply(dst.getBytes(), offset + start, 9);
    return 9;
  }

  public static long decodeInt64(PositionedByteRange src) {
    byte header = src.get();
    assert header == INT64_HEADER || header == INT64_HEADER_DESC;

    Order ord = header == INT64_HEADER ? Order.ASC : Order.DESC;
    long val = (ord.apply(src.get()) ^ 128) & 255;

    for (int i = 1; i < 8; ++i) {
      val = (val << 8) + (long) (ord.apply(src.get()) & 255);
    }

    return val;
  }

  public static int encodeFloat32(PositionedByteRange dst, float val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    int i = Float.floatToIntBits(val);
    i ^= i >> 31 | Integer.MIN_VALUE;
    dst.put(FLOAT32_HEADER)
        .put((byte) (i >> 24))
        .put((byte) (i >> 16))
        .put((byte) (i >> 8))
        .put((byte) i);
    ord.apply(dst.getBytes(), offset + start, 5);
    return 5;
  }

  public static float decodeFloat32(PositionedByteRange src) {
    byte header = src.get();

    assert header == FLOAT32_HEADER || header == FLOAT32_HEADER_DESC;

    Order ord = header == FLOAT32_HEADER ? Order.ASC : Order.DESC;
    int val = ord.apply(src.get()) & 255;

    for (int i = 1; i < 4; ++i) {
      val = (val << 8) + (ord.apply(src.get()) & 255);
    }

    val ^= ~val >> 31 | Integer.MIN_VALUE;
    return Float.intBitsToFloat(val);
  }

  public static int encodeFloat64(PositionedByteRange dst, double val, Order ord) {
    int offset = dst.getOffset();
    int start = dst.getPosition();
    long lng = Double.doubleToLongBits(val);
    lng ^= lng >> 63 | Long.MIN_VALUE;
    dst.put(FLOAT64_HEADER)
        .put((byte) ((int) (lng >> 56)))
        .put((byte) ((int) (lng >> 48)))
        .put((byte) ((int) (lng >> 40)))
        .put((byte) ((int) (lng >> 32)))
        .put((byte) ((int) (lng >> 24)))
        .put((byte) ((int) (lng >> 16)))
        .put((byte) ((int) (lng >> 8)))
        .put((byte) ((int) lng));
    ord.apply(dst.getBytes(), offset + start, 9);
    return 9;
  }

  public static double decodeFloat64(PositionedByteRange src) {
    byte header = src.get();

    assert header == FLOAT64_HEADER || header == FLOAT64_HEADER_DESC;

    Order ord = header == FLOAT64_HEADER ? Order.ASC : Order.DESC;
    long val = ord.apply(src.get()) & 255;

    for (int i = 1; i < 8; ++i) {
      val = (val << 8) + (long) (ord.apply(src.get()) & 255);
    }

    val ^= ~val >> 63 | Long.MIN_VALUE;
    return Double.longBitsToDouble(val);
  }

  public static int encodeJsonNode(PositionedByteRange dst, JsonNode val, Order ord) {
    try {
      String sVal = mapper.writeValueAsString(val);
      if (sVal.contains("\u0000")) {
        throw new IllegalArgumentException("Cannot encode String values containing '\\u0000'");
      } else {
        int offset = dst.getOffset();
        int start = dst.getPosition();
        dst.put(JSON_HEADER);
        dst.put(sVal.getBytes(UTF8));
        dst.put(STRING_FOOTER);
        ord.apply(dst.getBytes(), offset + start, dst.getPosition() - start);
        return dst.getPosition() - start;
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error encoding JSON", e);
    }
  }

  public static JsonNode decodeJsonNode(PositionedByteRange src) {
    byte header = src.get();
    if (header == NULL_HEADER || header == NULL_HEADER_DESC) {
      return null;
    } else {
      assert header == JSON_HEADER || header == JSON_HEADER_DESC;

      Order ord = header == JSON_HEADER ? Order.ASC : Order.DESC;
      byte[] a = src.getBytes();
      int offset = src.getOffset();
      int start = src.getPosition();
      byte terminator = ord.apply((byte) 0);
      int rawStartPos = offset + start;

      int rawTermPos;
      for (rawTermPos = rawStartPos; a[rawTermPos] != terminator; ++rawTermPos) {}

      src.setPosition(rawTermPos - offset + 1);
      try {
        if (Order.DESC == ord) {
          byte[] copy = new byte[rawTermPos - rawStartPos];
          System.arraycopy(a, rawStartPos, copy, 0, copy.length);
          ord.apply(copy);
          return mapper.readTree(copy);
        } else {
          return mapper.readTree(a, rawStartPos, rawTermPos - rawStartPos);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
