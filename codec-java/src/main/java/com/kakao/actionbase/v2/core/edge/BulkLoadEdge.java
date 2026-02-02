package com.kakao.actionbase.v2.core.edge;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BulkLoadEdge extends Edge {

  @JsonProperty(required = true)
  final boolean active;

  @JsonCreator
  public BulkLoadEdge(
      @JsonProperty(value = "active", required = true) boolean active,
      @JsonProperty(value = "ts", required = true) long ts,
      @JsonProperty(value = "src", required = true) Object src,
      @JsonProperty(value = "tgt", required = true) Object tgt,
      @JsonProperty(value = "props") Map<String, Object> props) {
    super(ts, src, tgt, props);
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BulkLoadEdge)) return false;
    BulkLoadEdge edge = (BulkLoadEdge) o;
    return active == edge.active
        && ts == edge.ts
        && src.equals(edge.src)
        && tgt.equals(edge.tgt)
        && props.equals(edge.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, ts, src, tgt, props);
  }

  @Override
  public String toString() {
    return "BulkLoadEdge("
        + "active="
        + active
        + ", ts="
        + ts
        + ", src="
        + src
        + ", tgt="
        + tgt
        + ", props="
        + props
        + ')';
  }
}
