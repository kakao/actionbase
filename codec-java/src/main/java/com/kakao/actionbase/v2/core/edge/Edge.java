package com.kakao.actionbase.v2.core.edge;

import com.kakao.actionbase.v2.core.types.EdgeSchema;
import com.kakao.actionbase.v2.core.types.Field;
import com.kakao.actionbase.v2.core.util.ULID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Edge {

  @JsonIgnore static final Map<String, Object> empty = Collections.emptyMap();

  @JsonProperty(required = true)
  final long ts;

  @JsonProperty(required = true)
  final Object src;

  @JsonProperty(required = true)
  final Object tgt;

  @JsonProperty final Map<String, Object> props;

  @JsonCreator
  public Edge(
      @JsonProperty(value = "ts", required = true) long ts,
      @JsonProperty(value = "src", required = true) Object src,
      @JsonProperty(value = "tgt", required = true) Object tgt,
      @JsonProperty(value = "props") Map<String, Object> props) {
    this.ts = ts;
    this.src = src;
    this.tgt = tgt;
    this.props = props != null ? props : empty;
  }

  public Edge(long ts, Object src, Object tgt) {
    this(ts, src, tgt, empty);
  }

  public long getTs() {
    return ts;
  }

  public Object getSrc() {
    return src;
  }

  public Object getTgt() {
    return tgt;
  }

  public Map<String, Object> getProps() {
    return props;
  }

  public Edge ensureType(EdgeSchema schema) {
    Object castedSrc = schema.getSrc().getDataType().castNotNull(src);
    Object castedTgt = schema.getTgt().getDataType().castNotNull(tgt);
    Map<String, Object> castedProps = new HashMap<>();
    props.forEach(
        (key, value) -> {
          Field field = schema.getField(key);
          if (field != null) {
            Object castedValue = field.getType().cast(value);
            castedProps.put(key, castedValue); // This allows null values
          }
        });

    return new Edge(ts, castedSrc, castedTgt, castedProps);
  }

  public TraceEdge toTraceEdge() {
    String traceId = ULID.random();
    return withTraceId(traceId);
  }

  public TraceEdge withTraceId(String traceId) {
    return new TraceEdge(ts, src, tgt, props, traceId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Edge)) return false;
    Edge edge = (Edge) o;
    return ts == edge.ts
        && src.equals(edge.src)
        && tgt.equals(edge.tgt)
        && props.equals(edge.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ts, src, tgt, props);
  }

  @Override
  public String toString() {
    return "Edge(" + "ts=" + ts + ", src=" + src + ", tgt=" + tgt + ", props=" + props + ')';
  }
}
