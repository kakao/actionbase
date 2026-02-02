package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.state.SpecialStateValue;
import com.kakao.actionbase.core.java.state.StateValue;

import java.util.Map;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableEdgeMutationResultItem.class)
@JsonDeserialize(as = ImmutableEdgeMutationResultItem.class)
public interface EdgeMutationResultItem {

  String status();

  String traceId();

  @Nullable
  EdgeState edge();

  static EdgeMutationResultItem fromV3(
      com.kakao.actionbase.core.java.state.Event event,
      com.kakao.actionbase.core.java.edge.EdgeState state,
      String status) {
    ImmutableEdgeState.Builder builder =
        ImmutableEdgeState.builder()
            .active(state.active())
            .ts(state.version())
            .src(state.source())
            .tgt(state.target());

    for (Map.Entry<String, StateValue> entry : state.properties().entrySet()) {
      Object value = entry.getValue().value();
      if (SpecialStateValue.isSpecialStateValue(value)) {
        builder.putProps(entry.getKey(), null);
      } else {
        builder.putProps(entry.getKey(), value);
      }
    }

    EdgeState edge = builder.build();

    return ImmutableEdgeMutationResultItem.builder()
        .status(status)
        .traceId(event.id())
        .edge(edge)
        .build();
  }
}
