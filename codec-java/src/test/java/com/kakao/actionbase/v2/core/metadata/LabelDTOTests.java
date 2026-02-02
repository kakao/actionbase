package com.kakao.actionbase.v2.core.metadata;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.v2.core.code.Index;
import com.kakao.actionbase.v2.core.code.hbase.Order;
import com.kakao.actionbase.v2.core.types.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LabelDTOTests {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testLabelSerialization() throws JsonProcessingException {
    LabelDTO label =
        new LabelDTO(
            "test.test",
            "desc",
            LabelType.INDEXED,
            new EdgeSchema(
                new VertexField(VertexType.LONG),
                new VertexField(VertexType.STRING),
                Collections.singletonList(new Field("created_at", DataType.LONG, false))),
            DirectionType.BOTH,
            "test_storage",
            Collections.singletonList(
                new Index(
                    "created_at",
                    Collections.singletonList(new Index.Field("created_at", Order.DESC)))),
            false,
            false,
            MutationMode.ASYNC);

    String jsonString = objectMapper.writeValueAsString(label);

    assertEquals(
        "{\"name\":\"test.test\",\"desc\":\"desc\",\"type\":\"INDEXED\",\"schema\":{\"src\":{\"type\":\"LONG\",\"desc\":\"\"},\"tgt\":{\"type\":\"STRING\",\"desc\":\"\"},\"fields\":[{\"name\":\"created_at\",\"type\":\"LONG\",\"nullable\":false,\"desc\":\"\"}]},\"dirType\":\"BOTH\",\"storage\":\"test_storage\",\"indices\":[{\"name\":\"created_at\",\"fields\":[{\"name\":\"created_at\",\"order\":\"DESC\"}],\"desc\":\"\"}],\"event\":false,\"readOnly\":false,\"mode\":\"ASYNC\"}",
        jsonString);
  }

  @Test
  void testGraphResponseDeserialization() throws JsonProcessingException {
    // topNCompositeKeys is removed, but the existing JSON should still be deserializable.
    String jsonString =
        "{\"name\":\"gift.like_product_v1\",\"desc\":\"Gift wish\",\"type\":\"INDEXED\",\"schema\":{\"src\":{\"type\":\"LONG\"},\"tgt\":{\"type\":\"STRING\"},\"fields\":[{\"name\":\"created_at\",\"type\":\"LONG\",\"nullable\":false},{\"name\":\"permission\",\"type\":\"STRING\",\"nullable\":true},{\"name\":\"memo\",\"type\":\"STRING\",\"nullable\":true}]},\"dirType\":\"BOTH\",\"storage\":\"hbase_sandbox\",\"indices\":[{\"id\":0,\"name\":\"created_at_desc\",\"fields\":[{\"name\":\"created_at\",\"order\":\"DESC\"}]}],\"topNCompositeKeys\":[{\"fields\":[\"tgt\"]}],\"event\":false,\"readOnly\":false,\"mode\":\"ASYNC\"}";

    LabelDTO label = objectMapper.readValue(jsonString, LabelDTO.class);

    assertEquals(LabelType.INDEXED, label.getType());
    assertEquals(VertexType.LONG, label.getSchema().getSrc().getType());
    assertEquals(VertexType.STRING, label.getSchema().getTgt().getType());
    assertEquals(3, label.getSchema().getFields().size());
    assertEquals("created_at", label.getSchema().getFields().get(0).getName());
    assertEquals(DataType.LONG, label.getSchema().getFields().get(0).getType());
    assertFalse(label.getSchema().getFields().get(0).isNullable());
    assertEquals("permission", label.getSchema().getFields().get(1).getName());
    assertEquals(DataType.STRING, label.getSchema().getFields().get(1).getType());
    assertTrue(label.getSchema().getFields().get(1).isNullable());
    assertEquals("memo", label.getSchema().getFields().get(2).getName());
    assertEquals(DataType.STRING, label.getSchema().getFields().get(2).getType());
    assertTrue(label.getSchema().getFields().get(2).isNullable());
    assertEquals(DirectionType.BOTH, label.getDirType());
    assertEquals(1, label.getIndices().size());
    assertEquals("created_at_desc", label.getIndices().get(0).getName());
    assertEquals(1, label.getIndices().get(0).getFields().size());
    assertEquals("created_at", label.getIndices().get(0).getFields().get(0).getName());
    assertEquals(Order.DESC, label.getIndices().get(0).getFields().get(0).getOrder());
    assertEquals("ASYNC", label.getMode().name());
  }
}
