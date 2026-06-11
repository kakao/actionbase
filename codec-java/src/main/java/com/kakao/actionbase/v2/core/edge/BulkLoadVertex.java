package com.kakao.actionbase.v2.core.edge;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bulk-load input for a Vertex row.
 *
 * <p>Mirrors {@link BulkLoadEdge} but exposes the vertex contract — a single {@code id} and
 * properties — instead of leaking the {@code (src, tgt="-")} encoding. {@code BulkEdgeEncoder}
 * converts this into a {@code BulkLoadEdge} with {@code src=id, tgt=VERTEX_MARKER} before
 * delegating to the shared encoding pipeline.
 *
 * <p>JSON shape:
 *
 * <pre>{"active": true, "ts": 1, "id": "user1", "props": {...}}</pre>
 */
public class BulkLoadVertex {

  /**
   * Reserved value used as the encoded {@code target} of a vertex row. Mirrors {@code
   * com.kakao.actionbase.core.Constants.VERTEX_MARKER} from the Kotlin core module — kept in sync
   * because codec-java is Java 8 and cannot depend on core.
   */
  public static final String VERTEX_MARKER = "-";

  @JsonProperty(required = true)
  final boolean active;

  @JsonProperty(required = true)
  final long ts;

  @JsonProperty(required = true)
  final Object id;

  @JsonProperty final Map<String, Object> props;

  @JsonCreator
  public BulkLoadVertex(
      @JsonProperty(value = "active", required = true) boolean active,
      @JsonProperty(value = "ts", required = true) long ts,
      @JsonProperty(value = "id", required = true) Object id,
      @JsonProperty(value = "props") Map<String, Object> props) {
    Objects.requireNonNull(id, "vertex id must not be null");
    if (VERTEX_MARKER.equals(id.toString())) {
      throw new IllegalArgumentException(
          "Vertex id cannot be '" + VERTEX_MARKER + "' (reserved marker)");
    }
    this.active = active;
    this.ts = ts;
    this.id = id;
    // Match BulkLoadEdge / Edge: props is never null on the instance, so callers don't NPE on
    // getProps() before the value reaches the encoder.
    this.props = props != null ? props : Collections.emptyMap();
  }

  public boolean isActive() {
    return active;
  }

  public long getTs() {
    return ts;
  }

  public Object getId() {
    return id;
  }

  public Map<String, Object> getProps() {
    return props;
  }

  /**
   * Lower this vertex into the shared {@link BulkLoadEdge} representation: {@code src=id,
   * tgt=VERTEX_MARKER}. The encoder pipeline keys all vertex behaviour off the LabelType, so the
   * concrete edge object is just a vehicle for the property map.
   */
  public BulkLoadEdge toBulkLoadEdge() {
    return new BulkLoadEdge(active, ts, id, VERTEX_MARKER, props);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BulkLoadVertex)) return false;
    BulkLoadVertex that = (BulkLoadVertex) o;
    return active == that.active && ts == that.ts && id.equals(that.id) && props.equals(that.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, ts, id, props);
  }

  @Override
  public String toString() {
    return "BulkLoadVertex(active="
        + active
        + ", ts="
        + ts
        + ", id="
        + id
        + ", props="
        + props
        + ')';
  }
}
