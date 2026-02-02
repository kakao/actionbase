package com.kakao.actionbase.core.java.pipeline.serializer;

import com.kakao.actionbase.core.java.pipeline.CDC;
import com.kakao.actionbase.core.java.pipeline.CDCV2;
import com.kakao.actionbase.core.java.pipeline.CDCV3Edge;
import com.kakao.actionbase.core.java.pipeline.CDCV3Vertex;
import com.kakao.actionbase.core.java.pipeline.MessageConstants;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CDCDeserializer extends JsonDeserializer<CDC> {

  @Override
  public CDC deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    ObjectNode node = mapper.readTree(p);

    if (!node.has(MessageConstants.VERSION_FIELD)) {
      throw new InvalidFormatException(p, "`version` is missing in CDC", node, CDC.class);
    }

    String version = node.get(MessageConstants.VERSION_FIELD).asText();
    switch (version) {
      case MessageConstants.VERSION_V2:
        return mapper.treeToValue(node, CDCV2.class);
      case MessageConstants.VERSION_V3:
        if (!node.has(MessageConstants.TYPE_FIELD)) {
          throw new InvalidFormatException(p, "`type` is missing in V3 CDC", node, CDC.class);
        }
        String type = node.get(MessageConstants.TYPE_FIELD).asText();
        switch (type) {
          case MessageConstants.EDGE_TYPE:
            return mapper.treeToValue(node, CDCV3Edge.class);
          case MessageConstants.VERTEX_TYPE:
            return mapper.treeToValue(node, CDCV3Vertex.class);
          default:
            throw new IllegalArgumentException("Unknown V3 type: " + type);
        }
      default:
        throw new IllegalArgumentException("Unknown version: " + version);
    }
  }
}
