package com.kakao.actionbase.core.java.edge;

import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.state.State;
import com.kakao.actionbase.core.java.state.StateValue;
import com.kakao.actionbase.core.java.state.base.EventCompanion;
import com.kakao.actionbase.core.java.state.base.StateCompanion;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.Collections;
import java.util.Map;

public class Edges {

  private Edges() {}

  public static final String DEFAULT_INDEX_NAME = "default";

  public static final String VERSION_FIELD = "version";

  public static final String SOURCE_FIELD = "source";

  public static final String TARGET_FIELD = "target";

  public static final String PROPERTIES_FIELD = "properties";

  public static final Map<String, Object> EMPTY_PROPS = Collections.emptyMap();

  static EdgePayload toPayload(EdgeState state) {
    ImmutableEdgePayload.Builder builder =
        ImmutableEdgePayload.builder()
            .version(state.version())
            .source(state.source())
            .target(state.target());

    for (Map.Entry<String, StateValue> entry : state.properties().entrySet()) {
      builder.putProperties(entry.getKey(), entry.getValue().value());
    }

    return builder.build();
  }

  static EdgeEvent toEvent(EdgePayload payload, EventType type) {
    return ImmutableEdgeEvent.builder()
        .type(type)
        .id(EventCompanion.generateRandomId())
        .version(payload.version())
        .source(payload.source())
        .target(payload.target())
        .properties(payload.properties())
        .build();
  }

  static EdgeEvent toEvent(EdgePayload payload, EventType type, String requestId) {
    return ImmutableEdgeEvent.builder()
        .type(type)
        .id(EventCompanion.generateRandomId())
        .requestId(requestId)
        .version(payload.version())
        .source(payload.source())
        .target(payload.target())
        .properties(payload.properties())
        .build();
  }

  static EdgeState transit(EdgeState state, EdgeEvent event, StructType schema) {
    if (!state.keyEquals(event)) {
      throw new IllegalArgumentException(
          String.format("State and event must be same. state: %s, event: %s", state, event));
    }

    State newState = StateCompanion.transit(state, event, schema);
    return ImmutableEdgeState.builder()
        .from(newState)
        .source(event.source())
        .target(event.target())
        .properties(newState.properties())
        .build();
  }

  static EdgeState toState(EdgeEvent event, StructType schema) {
    EdgeState initialState = initialStateOf(event.source(), event.target());
    return transit(initialState, event, schema);
  }

  public static EdgeState initialStateOf(Object source, Object target) {
    return ImmutableEdgeState.builder().active(false).source(source).target(target).build();
  }
}
