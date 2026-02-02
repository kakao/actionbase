package com.kakao.actionbase.v2.core.edge;

import com.kakao.actionbase.v2.core.types.EdgeSchema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TraceEdge extends Edge {

  @JsonProperty final String traceId;

  TraceEdge(long ts, Object src, Object tgt, Map<String, Object> props, String traceId) {
    super(ts, src, tgt, props);
    this.traceId = traceId;
  }

  @Override
  public TraceEdge ensureType(EdgeSchema schema) {
    return super.ensureType(schema).withTraceId(traceId);
  }

  public String getTraceId() {
    return traceId;
  }
}
