package com.kakao.actionbase.v2.core.edge;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EdgeValue {

  @JsonIgnore static final Map<String, Object> empty = Collections.emptyMap();

  @JsonProperty(required = true)
  final long ts;

  @JsonProperty final Map<String, Object> props;

  @JsonCreator
  public EdgeValue(
      @JsonProperty(value = "ts", required = true) long ts,
      @JsonProperty(value = "props") Map<String, Object> props) {
    this.ts = ts;
    this.props = props != null ? props : empty;
  }

  public Edge toEdge(Object src, Object tgt) {
    return new Edge(ts, src, tgt, props);
  }
}
