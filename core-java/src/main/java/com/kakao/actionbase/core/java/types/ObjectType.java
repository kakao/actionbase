package com.kakao.actionbase.core.java.types;

import com.kakao.actionbase.core.java.annotation.NotNull;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableObjectType.class)
@JsonDeserialize(as = ImmutableObjectType.class)
interface ObjectType extends DataType<ObjectNode> {

  String typeName = "object";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default ObjectNode castNotNull(@NotNull Object value) {
    if (value instanceof ObjectNode) {
      return (ObjectNode) value;
    }

    return ActionbaseObjectMapper.INSTANCE.valueToTree(value);
  }
}
