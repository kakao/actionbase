package com.kakao.actionbase.core.java.edge.index;

import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.metadata.v3.TableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.Index;
import com.kakao.actionbase.core.java.metadata.v3.common.IndexField;
import com.kakao.actionbase.core.java.metadata.v3.common.SystemProperties;
import com.kakao.actionbase.core.java.state.StateValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EdgeIndexes {

  public static List<EncodableEdgeIndex> build(EdgeState state, TableDescriptor<?> table) {
    EdgeSchema edgeSchema = (EdgeSchema) table.schema();
    List<Direction> directions = edgeSchema.direction().directions();
    Map<String, Object> properties = new HashMap<>();
    for (Map.Entry<String, StateValue> entry : state.properties().entrySet()) {
      properties.put(entry.getKey(), entry.getValue().value());
    }

    List<EncodableEdgeIndex> edgeIndexes = new ArrayList<>();
    for (Index index : edgeSchema.indexes()) {
      for (Direction direction : directions) {
        ImmutableEncodableEdgeIndex.Builder builder =
            ImmutableEncodableEdgeIndex.builder()
                .version(state.version())
                .source(state.source())
                .target(state.target())
                .properties(properties)
                .direction(direction)
                .indexCode(index.code())
                .tableCode(table.code());

        for (IndexField field : index.fields()) {
          SystemProperties systemProperty = SystemProperties.getOrNull(field.field());
          if (systemProperty == null) {
            StateValue stateValue = state.properties().get(field.field());
            if (stateValue == null) {
              builder.addIndexValues(ImmutableEncodableIndexValue.of(null, field.order()));
            } else {
              builder.addIndexValues(
                  ImmutableEncodableIndexValue.of(stateValue.value(), field.order()));
            }
          } else {
            switch (systemProperty) {
              case VERSION:
                builder.addIndexValues(
                    ImmutableEncodableIndexValue.of(state.version(), field.order()));
                break;
              case SOURCE:
                builder.addIndexValues(
                    ImmutableEncodableIndexValue.of(state.source(), field.order()));
                break;
              case TARGET:
                builder.addIndexValues(
                    ImmutableEncodableIndexValue.of(state.target(), field.order()));
                break;
            }
          }
        }

        edgeIndexes.add(builder.build());
      }
    }
    return edgeIndexes;
  }
}
