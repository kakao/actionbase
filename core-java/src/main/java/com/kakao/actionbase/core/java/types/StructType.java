package com.kakao.actionbase.core.java.types;

import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.types.common.StructField;
import com.kakao.actionbase.core.java.util.HashUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Value.Immutable
@JsonSerialize(as = ImmutableStructType.class)
@JsonDeserialize(as = ImmutableStructType.class)
public interface StructType extends DataType<ObjectNode> {

  String typeName = "struct";

  @Override
  @Value.Derived
  default String type() {
    return typeName;
  }

  List<StructField> fields();

  @Value.Derived
  @JsonIgnore
  default int size() {
    return fields().size();
  }

  // Cache index map with field names as keys
  @Value.Auxiliary
  @Value.Derived
  @JsonIgnore
  default Map<String, Integer> fieldIndexMap() {
    //    StructField[] fields = fields();
    //    Map<String, Integer> indexMap = new HashMap<>(fields.length);
    //    for (int i = 0; i < fields.length; i++) {
    //      indexMap.put(fields[i].name(), i);
    //    }
    //    return indexMap;
    // convert it to a stream and then collect it to a map
    return fields().stream()
        .collect(HashMap::new, (map, field) -> map.put(field.name(), map.size()), HashMap::putAll);
  }

  @Value.Auxiliary
  @Value.Derived
  @JsonIgnore
  default Map<Integer, String> hashToFieldNameMap() {
    return fields().stream()
        .collect(
            HashMap::new,
            (map, field) -> map.put(HashUtils.stringHash(field.name()), field.name()),
            HashMap::putAll);
  }

  @Value.Auxiliary
  @Value.Derived
  @JsonIgnore
  default List<String> fieldNames() {
    return fields().stream().map(StructField::name).collect(Collectors.toList());
  }

  default int getFieldIndex(String name) {
    return fieldIndexMap().getOrDefault(name, -1);
  }

  default StructField getField(String name) {
    int index = getFieldIndex(name);
    return index >= 0 ? fields().get(index) : null;
  }

  default StructField getField(int index) {
    return fields().get(index);
  }

  /**
   * Checks if a field with the specified name exists.
   *
   * @param name Field name to check
   * @return true if field exists, false otherwise
   */
  default boolean hasField(String name) {
    return getFieldIndex(name) >= 0;
  }

  @Override
  default ObjectNode castNotNull(Object value) {
    if (value instanceof ObjectNode) {
      return (ObjectNode) value;
    }
    JsonNode tree = ActionbaseObjectMapper.INSTANCE.valueToTree(value);
    if (tree instanceof ObjectNode) {
      return (ObjectNode) tree;
    } else {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to ObjectNode", value.getClass().getName()));
    }
  }

  @Value.Auxiliary
  default String toTypeString(StructType self) {
    StringBuilder sb = new StringBuilder();
    sb.append(typeName).append("<");

    List<StructField> fields = fields();
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      StructField field = fields.get(i);
      sb.append(field.name()).append(":").append(field.type());
    }

    sb.append(">");
    return sb.toString();
  }

  static ImmutableStructType.Builder builder() {
    return ImmutableStructType.builder();
  }

  abstract class Builder {

    public abstract ImmutableStructType.Builder addFields(StructField field);

    public final ImmutableStructType.Builder addField(
        String name, DataType<?> type, String comment, boolean nullable) {
      return addFields(
          StructField.builder().name(name).type(type).comment(comment).nullable(nullable).build());
    }

    public final ImmutableStructType.Builder addField(String name, DataType<?> type) {
      return addField(name, type, "", true);
    }

    public final ImmutableStructType.Builder addField(
        String name, DataType<?> type, String comment) {
      return addField(name, type, comment, true);
    }

    public final ImmutableStructType.Builder addField(
        String name, DataType<?> type, boolean nullable) {
      return addField(name, type, "", nullable);
    }
  }
}
