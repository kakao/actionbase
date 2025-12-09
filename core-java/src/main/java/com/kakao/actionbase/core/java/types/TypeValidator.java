package com.kakao.actionbase.core.java.types;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TypeValidator {

  private TypeValidator() {}

  public static void validateValue(Object value) {
    if (value == null) {
      return;
    }
    Class<?> cls = value.getClass();

    boolean isSupportedType =
        cls == Boolean.class
            || cls == Integer.class
            || cls == Long.class
            || cls == String.class
            || cls == ObjectNode.class
            || cls == ArrayNode.class;

    //    if (!isSupportedType) {
    //      throw new ActionbaseUnssportedTypeException("Unsupported type: " + cls);
    //    }
  }

  public static void validateProperties(Map<String, Object> properties) {
    for (Object value : properties.values()) {
      validateValue(value);
    }
  }
}
