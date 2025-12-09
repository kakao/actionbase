package com.kakao.actionbase.v2.core.types;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum DataType {
  BYTE(Byte.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Byte
          ? value
          : value instanceof Number
              ? ((Number) value).byteValue()
              : Byte.parseByte(value.toString());
    }
  },
  SHORT(Short.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Short
          ? value
          : value instanceof Number
              ? ((Number) value).shortValue()
              : Short.parseShort(value.toString());
    }
  },
  INT(Integer.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Integer
          ? value
          : value instanceof Number
              ? ((Number) value).intValue()
              : Integer.parseInt(value.toString());
    }
  },
  LONG(Long.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Long
          ? value
          : value instanceof Number
              ? ((Number) value).longValue()
              : Long.parseLong(value.toString());
    }
  },
  BOOLEAN(Boolean.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
    }
  },
  FLOAT(Float.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Float
          ? value
          : value instanceof Number
              ? ((Number) value).floatValue()
              : Float.parseFloat(value.toString());
    }
  },
  DOUBLE(Double.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof Double
          ? value
          : value instanceof Number
              ? ((Number) value).doubleValue()
              : Double.parseDouble(value.toString());
    }
  },
  DECIMAL(BigDecimal.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof BigDecimal
          ? value
          : value instanceof Number
              ? BigDecimal.valueOf(((Number) value).doubleValue())
              : new BigDecimal(value.toString());
    }
  },
  STRING(String.class) {
    @Override
    protected Object forceCast(Object value) {
      return value.toString();
    }
  },
  JSON(JsonNode.class) {
    @Override
    protected Object forceCast(Object value) {
      return value instanceof JsonNode ? value : objectMapper.valueToTree(value);
    }
  };

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final Class<?> clazz;

  DataType(Class<?> clazz) {
    this.clazz = clazz;
  }

  public Class<?> getClazz() {
    return clazz;
  }

  protected abstract Object forceCast(Object value) throws IllegalArgumentException;

  public final Object cast(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return forceCast(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public final Object castNotNull(Object value) {
    if (value == null) {
      throw new NullPointerException("value cannot be null");
    }
    Object result = cast(value);
    if (result == null) {
      throw new IllegalArgumentException(
          "Casting resulted in null, indicating an invalid input or type mismatch");
    }
    return result;
  }
}
