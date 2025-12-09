package com.kakao.actionbase.core.java.types;

import com.kakao.actionbase.core.java.annotation.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ImmutableStringType.class, name = StringType.typeName),
  @JsonSubTypes.Type(value = ImmutableStructType.class, name = StructType.typeName),
  @JsonSubTypes.Type(value = ImmutableShortType.class, name = ShortType.typeName),
  @JsonSubTypes.Type(value = ImmutableIntType.class, name = IntType.typeName),
  @JsonSubTypes.Type(value = ImmutableLongType.class, name = LongType.typeName),
  @JsonSubTypes.Type(value = ImmutableDoubleType.class, name = DoubleType.typeName),
  @JsonSubTypes.Type(value = ImmutableBooleanType.class, name = BooleanType.typeName),
  @JsonSubTypes.Type(value = ImmutableArrayType.class, name = ArrayType.typeName),
  @JsonSubTypes.Type(value = ImmutableObjectType.class, name = ObjectType.typeName),
})
public interface DataType<T> {

  @JsonIgnore
  String type();

  default T cast(Object value) {
    if (value == null) {
      return null;
    }
    return castNotNull(value);
  }

  T castNotNull(@NotNull Object value);

  DataType<String> STRING = ImmutableStringType.of();

  DataType<ObjectNode> OBJECT = ImmutableObjectType.of();

  DataType<Long> LONG = ImmutableLongType.of();

  DataType<Integer> INT = ImmutableIntType.of();

  DataType<Short> SHORT = ImmutableShortType.of();

  DataType<Double> DOUBLE = ImmutableDoubleType.of();

  DataType<Boolean> BOOLEAN = ImmutableBooleanType.of();

  static DataType<?> valueOf(String type) {
    switch (type) {
      case "class java.lang.String":
      case "string":
        return DataType.STRING;
      case "long":
        return DataType.LONG;
      case "double":
        return DataType.DOUBLE;
      case "boolean":
        return DataType.BOOLEAN;
      default:
        throw new IllegalArgumentException("Unknown type: " + type);
    }
  }
}
