package com.kakao.actionbase.core.java.vertex;

import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.state.State;
import com.kakao.actionbase.core.java.state.StateValue;
import com.kakao.actionbase.core.java.state.base.StateCompanion;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.Collections;
import java.util.Map;

public class VerticeCompanion {

  private VerticeCompanion() {}

  public static final String VERSION = "version";

  public static final String KEY_FIELD = "key";

  public static final String PROPERTIES_FIELD = "properties";

  public static final Map<String, Object> EMPTY_PROPS = Collections.emptyMap();

  static VertexPayload toPayload(VertexState state) {
    ImmutableVertexPayload.Builder builder = ImmutableVertexPayload.builder().key(state.key());

    for (Map.Entry<String, StateValue> entry : state.properties().entrySet()) {
      builder.putProperties(entry.getKey(), entry.getValue().value());
    }

    return builder.build();
  }

  static VertexEvent toEvent(VertexPayload payload, EventType type) {
    return ImmutableVertexEvent.builder()
        .type(type)
        .key(payload.key())
        .properties(payload.properties())
        .build();
  }

  static VertexEvent toEvent(VertexPayload payload, EventType type, long version) {
    return ImmutableVertexEvent.builder()
        .type(type)
        .version(version)
        .key(payload.key())
        .properties(payload.properties())
        .build();
  }

  static VertexEvent toEvent(
      VertexPayload payload, EventType type, long version, String requestId) {
    return ImmutableVertexEvent.builder()
        .type(type)
        .version(version)
        .requestId(requestId)
        .key(payload.key())
        .properties(payload.properties())
        .build();
  }

  static VertexState toState(VertexEvent event, StructType schema) {
    VertexState initialState = initialStateOf(event.key());
    State state = StateCompanion.transit(initialState, event, schema);
    return ImmutableVertexState.builder().key(event.key()).properties(state.properties()).build();
  }

  public static VertexState initialStateOf(Object key) {
    return ImmutableVertexState.builder().active(false).key(key).build();
  }
}
