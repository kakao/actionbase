package com.kakao.actionbase.core.java.metadata.v2;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.metadata.v2.common.*;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.test.migration.MigratedFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TableDescriptorTest {

  static final String V2_JSON_STRING =
      "{\n"
          + "  \"active\": true,\n"
          + "  \"name\": \"social.user_favorite_article_v1_20240101_000000\",\n"
          + "  \"desc\": \"User article bookmarks\",\n"
          + "  \"type\": \"INDEXED\",\n"
          + "  \"schema\": {\n"
          + "    \"src\": {\n"
          + "      \"type\": \"LONG\",\n"
          + "      \"desc\": \"Platform user identifier (puid)\"\n"
          + "    },\n"
          + "    \"tgt\": {\n"
          + "      \"type\": \"LONG\",\n"
          + "      \"desc\": \"Content article identifier (article_id)\"\n"
          + "    },\n"
          + "    \"fields\": [\n"
          + "      {\n"
          + "        \"name\": \"createdAt\",\n"
          + "        \"type\": \"LONG\",\n"
          + "        \"nullable\": false,\n"
          + "        \"desc\": \"Creation timestamp={system.currentTimeMillis()}\"\n"
          + "      },\n"
          + "      {\n"
          + "        \"name\": \"permission\",\n"
          + "        \"type\": \"STRING\",\n"
          + "        \"nullable\": false,\n"
          + "        \"desc\": \"Visibility setting={private | public}\"\n"
          + "      },\n"
          + "      {\n"
          + "        \"name\": \"receivedFrom\",\n"
          + "        \"type\": \"STRING\",\n"
          + "        \"nullable\": false,\n"
          + "        \"desc\": \"Discovery source={self | shared | recommended}\"\n"
          + "      }\n"
          + "    ]\n"
          + "  },\n"
          + "  \"dirType\": \"BOTH\",\n"
          + "  \"storage\": \"st3_social_user_favorite_article_v1_20240101_000000\",\n"
          + "  \"indices\": [\n"
          + "    {\n"
          + "      \"name\": \"permission_created_at_desc\",\n"
          + "      \"fields\": [\n"
          + "        {\n"
          + "          \"name\": \"permission\",\n"
          + "          \"order\": \"ASC\"\n"
          + "        },\n"
          + "        {\n"
          + "          \"name\": \"createdAt\",\n"
          + "          \"order\": \"DESC\"\n"
          + "        }\n"
          + "      ],\n"
          + "      \"desc\": \"Permission/creation time descending index\"\n"
          + "    },\n"
          + "    {\n"
          + "      \"name\": \"created_at_desc\",\n"
          + "      \"fields\": [\n"
          + "        {\n"
          + "          \"name\": \"createdAt\",\n"
          + "          \"order\": \"DESC\"\n"
          + "        }\n"
          + "      ],\n"
          + "      \"desc\": \"Creation time descending index\"\n"
          + "    }\n"
          + "  ],\n"
          + "  \"topNCompositeKeys\": [\n"
          + "    {\n"
          + "      \"fields\": [\n"
          + "        \"permission\",\n"
          + "        \"tgt\"\n"
          + "      ],\n"
          + "      \"desc\": \"Composite key for bookmark count TopN queries\"\n"
          + "    }\n"
          + "  ],\n"
          + "  \"event\": false,\n"
          + "  \"readOnly\": false,\n"
          + "  \"mode\": \"SYNC\"\n"
          + "}";

  ObjectMapper objectMapper;
  LabelDescriptor descriptor;

  @BeforeEach
  void setup() throws JsonProcessingException {
    objectMapper = new ObjectMapper();
    descriptor = objectMapper.readValue(V2_JSON_STRING, LabelDescriptor.class);
  }

  @Test
  @MigratedFrom("kc.graph.metadata.LabelDTOTests.testGraphResponseDeserialization")
  void testDeserialization() {
    assertTrue(descriptor.active());
    assertEquals("social.user_favorite_article_v1_20240101_000000", descriptor.name());
    assertEquals("User article bookmarks", descriptor.desc());
    assertEquals(LabelType.INDEXED, descriptor.type());
    assertEquals(DirectionType.BOTH, descriptor.dirType());
    assertEquals("st3_social_user_favorite_article_v1_20240101_000000", descriptor.storage());
    assertFalse(descriptor.event());
    assertFalse(descriptor.readOnly());
    assertEquals(MutationMode.SYNC, descriptor.mode());

    // Schema assertions
    assertEquals(VertexType.LONG, descriptor.schema().src().type());
    assertEquals("Platform user identifier (puid)", descriptor.schema().src().desc());
    assertEquals(VertexType.LONG, descriptor.schema().tgt().type());
    assertEquals("Content article identifier (article_id)", descriptor.schema().tgt().desc());

    // Fields assertions
    assertEquals(3, descriptor.schema().fields().size());

    // createdAt field
    Field createdAtField = descriptor.schema().fields().get(0);
    assertEquals("createdAt", createdAtField.name());
    assertEquals(DataType.LONG, createdAtField.type());
    assertFalse(createdAtField.nullable());
    assertEquals("Creation timestamp={system.currentTimeMillis()}", createdAtField.desc());

    // permission field
    Field permissionField = descriptor.schema().fields().get(1);
    assertEquals("permission", permissionField.name());
    assertEquals(DataType.STRING, permissionField.type());
    assertFalse(permissionField.nullable());
    assertEquals("Visibility setting={private | public}", permissionField.desc());

    // receivedFrom field
    Field receivedFromField = descriptor.schema().fields().get(2);
    assertEquals("receivedFrom", receivedFromField.name());
    assertEquals(DataType.STRING, receivedFromField.type());
    assertFalse(receivedFromField.nullable());
    assertEquals("Discovery source={self | shared | recommended}", receivedFromField.desc());

    // Indices assertions
    assertEquals(2, descriptor.indices().size());

    // First index
    Index firstIndex = descriptor.indices().get(0);
    assertEquals("permission_created_at_desc", firstIndex.name());
    assertEquals("Permission/creation time descending index", firstIndex.desc());
    assertEquals(2, firstIndex.fields().size());
    assertEquals("permission", firstIndex.fields().get(0).name());
    assertEquals(Order.ASC, firstIndex.fields().get(0).order());
    assertEquals("createdAt", firstIndex.fields().get(1).name());
    assertEquals(Order.DESC, firstIndex.fields().get(1).order());

    // Second index
    Index secondIndex = descriptor.indices().get(1);
    assertEquals("created_at_desc", secondIndex.name());
    assertEquals("Creation time descending index", secondIndex.desc());
    assertEquals(1, secondIndex.fields().size());
    assertEquals("createdAt", secondIndex.fields().get(0).name());
    assertEquals(Order.DESC, secondIndex.fields().get(0).order());

    // TopN composite keys assertions
    assertEquals(1, descriptor.topNCompositeKeys().size());
    TopNCompositeKey compositeKey = descriptor.topNCompositeKeys().get(0);
    assertEquals(2, compositeKey.fields().size());
    assertEquals("permission", compositeKey.fields().get(0));
    assertEquals("tgt", compositeKey.fields().get(1));
    assertEquals("Composite key for bookmark count TopN queries", compositeKey.desc());
  }
}
