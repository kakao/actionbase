package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.edge.BulkLoadEdge;
import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Supported edge types:
 *
 * <pre>
 *  v2                | v3         | Description
 * -------------------|------------|----------------------------
 *  HASH              | N/A        | Stores only EdgeState, EdgeCount
 *  INDEXED           | EDGE       | Stores EdgeState, EdgeIndex, EdgeCount
 *  IMMUTABLE_INDEXED | N/A        | (As of 2025/08/10) Currently not used anywhere
 *  N/A               | MULTI_EDGE | Stores multiple edges for the same source, target pair based on id
 * </pre>
 */
public class BulkEdgeEncoder {

  /** Property key used to extract Edge ID from MultiEdge */
  static final String ID_FIELD_ON_EVENT = "_id";

  static final String SOURCE_FIELD_ON_STATE = "_source";
  static final String TARGET_FIELD_ON_STATE = "_target";

  public static <T> List<KeyFieldValue<T>> bulkEncodeAll(
      EdgeEncoder<T> encoder, BulkLoadEdge bulkLoadEdge, LabelDTO label) {
    LabelType labelType = label.getType();

    assert labelType.isBulkEncodingSupported();

    int labelId = label.getId();
    Active active = bulkLoadEdge.isActive() ? Active.ACTIVE : Active.INACTIVE;
    Edge castedEdge = bulkLoadEdge.ensureType(label.getSchema());

    List<KeyFieldValue<T>> edges = new ArrayList<>();

    // Special handling for MultiEdge
    // - Keep existing HASH, INDEXED as is, and for MultiEdge, create separate edges based on ID and
    // reuse existing encoder
    Edge edgeForEdgeState = castedEdge;
    Map<String, Object> multiEdgeProps = castedEdge.getProps();
    Object edgeId = null;
    if (labelType == LabelType.MULTI_EDGE) {
      // used v3 naming conventions.
      long version = castedEdge.getTs();
      edgeId = castedEdge.getProps().get(ID_FIELD_ON_EVENT);
      Object source = castedEdge.getSrc();
      Object target = castedEdge.getTgt();
      Map<String, Object> properties = new HashMap<>(castedEdge.getProps());
      properties.remove(ID_FIELD_ON_EVENT);
      properties.put(SOURCE_FIELD_ON_STATE, source);
      properties.put(TARGET_FIELD_ON_STATE, target);
      edgeForEdgeState = new Edge(version, edgeId, edgeId, properties);
      multiEdgeProps = properties;
    }

    // encode a single hash edge
    if (labelType == LabelType.HASH
        || labelType == LabelType.INDEXED
        || labelType == LabelType.MULTI_EDGE) {
      EncodedKey<T> key = encoder.encodeHashEdgeKey(edgeForEdgeState, labelId);
      Long insertTs = null;
      Long deleteTs = null;
      if (active == Active.ACTIVE) {
        insertTs = edgeForEdgeState.getTs();
      } else {
        deleteTs = edgeForEdgeState.getTs();
      }
      T value =
          encoder.encodeHashEdgeValue(
              HashEdgeValue.from(
                  active,
                  edgeForEdgeState.getTs(),
                  edgeForEdgeState.getProps(),
                  insertTs,
                  deleteTs));
      edges.add(new KeyFieldValue<>(key.key, key.field, value));
    }

    if (active == Active.ACTIVE) {
      // encode indexed edges
      if (labelType == LabelType.INDEXED || labelType == LabelType.IMMUTABLE_INDEXED) {
        // Keep existing code
        edges.addAll(
            encoder.encodeAllIndexedEdges(
                castedEdge, label.getDirType(), labelId, label.getIndices()));
      } else if (labelType == LabelType.MULTI_EDGE) {
        // For MultiEdge, create separate OUT/IN edges based on direction and reuse existing encoder
        // BOTH: Split into two edges: src->edgeId (OUT), edgeId->tgt (IN)
        multiEdgeProps.put(SOURCE_FIELD_ON_STATE, castedEdge.getSrc());
        multiEdgeProps.put(TARGET_FIELD_ON_STATE, castedEdge.getTgt());

        if (label.getDirType() == DirectionType.BOTH) {
          Edge outEdge = new Edge(castedEdge.getTs(), castedEdge.getSrc(), edgeId, multiEdgeProps);
          Edge inEdge = new Edge(castedEdge.getTs(), edgeId, castedEdge.getTgt(), multiEdgeProps);
          edges.addAll(
              encoder.encodeAllIndexedEdges(
                  outEdge, DirectionType.OUT, labelId, label.getIndices()));

          edges.addAll(
              encoder.encodeAllIndexedEdges(inEdge, DirectionType.IN, labelId, label.getIndices()));
        } else if (label.getDirType() == DirectionType.OUT) {
          // OUT: Create src->edgeId edge
          Edge outEdge = new Edge(castedEdge.getTs(), castedEdge.getSrc(), edgeId, multiEdgeProps);
          edges.addAll(
              encoder.encodeAllIndexedEdges(
                  outEdge, DirectionType.OUT, labelId, label.getIndices()));
        } else if (label.getDirType() == DirectionType.IN) {
          // IN: Create edgeId->tgt edge
          Edge inEdge = new Edge(castedEdge.getTs(), edgeId, castedEdge.getTgt(), multiEdgeProps);
          edges.addAll(
              encoder.encodeAllIndexedEdges(inEdge, DirectionType.IN, labelId, label.getIndices()));
        }
      }

      // EdgeCount is compatible with existing code as is
      if (label.getDirType() == DirectionType.BOTH) {
        T outboundKey = encoder.encodeCounterEdgeKey(castedEdge, Direction.OUT, labelId);
        T inboundKey = encoder.encodeCounterEdgeKey(castedEdge, Direction.IN, labelId);
        edges.add(new KeyFieldValue<>(encoder.getEmpty(), outboundKey));
        edges.add(new KeyFieldValue<>(encoder.getEmpty(), inboundKey));
      } else if (label.getDirType() == DirectionType.OUT) {
        T outboundKey = encoder.encodeCounterEdgeKey(castedEdge, Direction.OUT, labelId);
        edges.add(new KeyFieldValue<>(encoder.getEmpty(), outboundKey));
      } else if (label.getDirType() == DirectionType.IN) {
        T inboundKey = encoder.encodeCounterEdgeKey(castedEdge, Direction.IN, labelId);
        edges.add(new KeyFieldValue<>(encoder.getEmpty(), inboundKey));
      }
    }

    return edges;
  }
}
