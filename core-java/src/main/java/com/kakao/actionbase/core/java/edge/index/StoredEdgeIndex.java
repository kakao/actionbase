package com.kakao.actionbase.core.java.edge.index;

import com.kakao.actionbase.core.java.annotation.AllowNulls;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;
import com.kakao.actionbase.core.java.state.SpecialStateValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableStoredEdgeIndex.class)
@JsonDeserialize(as = ImmutableStoredEdgeIndex.class)
public interface StoredEdgeIndex {

  long version();

  Object directedSource();

  Object directedTarget();

  @AllowNulls
  Map<Integer, Object> properties();

  Direction direction();

  int tableCode();

  int indexCode();

  @AllowNulls
  List<Object> indexValues();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Object source() {
    if (direction() == Direction.OUT) {
      return directedSource();
    } else {
      return directedTarget();
    }
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Object target() {
    if (direction() == Direction.OUT) {
      return directedTarget();
    } else {
      return directedSource();
    }
  }

  default EdgePayload toEdgePayload(Map<Integer, String> hashToFieldNameMap) {
    Map<String, Object> properties = new HashMap<>();
    for (Map.Entry<Integer, Object> entry : properties().entrySet()) {
      String fieldName = hashToFieldNameMap.get(entry.getKey());
      Object value = entry.getValue();
      if (SpecialStateValue.isSpecialStateValue(value)) {
        value = null;
      }
      if (fieldName != null) {
        properties.put(fieldName, value);
      } else {
        properties.put(String.valueOf(entry.getKey()), value);
      }
    }

    ImmutableEdgePayload.Builder builder =
        ImmutableEdgePayload.builder().version(version()).properties(properties);

    builder.source(source());
    builder.target(target());

    return builder.build();
  }
}
