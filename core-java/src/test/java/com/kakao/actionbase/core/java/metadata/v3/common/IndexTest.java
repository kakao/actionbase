package com.kakao.actionbase.core.java.metadata.v3.common;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.util.HashUtils;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Index Domain Tests")
class IndexTest {

  private static final String TEST_INDEX = "test_index";
  private static final String TEST_COMMENT = "Test index comment";
  private static final IndexField TEST_FIELD1 = ImmutableIndexField.of("field1", Order.ASC);
  private static final IndexField TEST_FIELD2 = ImmutableIndexField.of("field2", Order.DESC);
  private static final List<IndexField> TEST_FIELDS = Arrays.asList(TEST_FIELD1, TEST_FIELD2);

  @Nested
  @DisplayName("Construction")
  class ConstructionTests {

    @Test
    @DisplayName("Can create Index with required fields")
    void testConstruction() {
      // Create using builder
      Index index = ImmutableIndex.builder().index(TEST_INDEX).fields(TEST_FIELDS).build();

      // Verify field values
      assertEquals(TEST_INDEX, index.index());
      assertEquals("", index.comment()); // Default value
      assertEquals(TEST_FIELDS, index.fields());
      assertEquals(2, index.fields().size());
      assertEquals(TEST_FIELD1.field(), index.fields().get(0).field());
      assertEquals(TEST_FIELD1.order(), index.fields().get(0).order());
      assertEquals(TEST_FIELD2.field(), index.fields().get(1).field());
      assertEquals(TEST_FIELD2.order(), index.fields().get(1).order());
    }

    @Test
    @DisplayName("Can create Index with all fields")
    void testFullConstruction() {
      // Create using builder with all fields
      Index index =
          ImmutableIndex.builder()
              .index(TEST_INDEX)
              .comment(TEST_COMMENT)
              .fields(TEST_FIELDS)
              .build();

      // Verify field values
      assertEquals(TEST_INDEX, index.index());
      assertEquals(TEST_COMMENT, index.comment());
      assertEquals(TEST_FIELDS, index.fields());
    }
  }

  @Nested
  @DisplayName("Data Conversion")
  class DataConversionTests {

    private Index createTestIndex() {
      return ImmutableIndex.builder()
          .index(TEST_INDEX)
          .comment(TEST_COMMENT)
          .fields(TEST_FIELDS)
          .build();
    }

    @Test
    @DisplayName("JSON serialization/deserialization")
    void testJsonSerialization() {
      // Create original Index
      Index original = createTestIndex();

      // Convert to JSON
      String json = ActionbaseObjectMapper.toJson(original);

      // Convert back from JSON
      Index fromJson = ActionbaseObjectMapper.fromJson(json, Index.class);

      // Verify field values
      assertEquals(original.index(), fromJson.index());
      assertEquals(original.comment(), fromJson.comment());
      assertEquals(original.fields().size(), fromJson.fields().size());
      assertEquals(original.fields().get(0).field(), fromJson.fields().get(0).field());
      assertEquals(original.fields().get(0).order(), fromJson.fields().get(0).order());
      assertEquals(original.fields().get(1).field(), fromJson.fields().get(1).field());
      assertEquals(original.fields().get(1).order(), fromJson.fields().get(1).order());
    }
  }

  @Nested
  @DisplayName("Domain Rules")
  class DomainRuleTests {

    @Test
    @DisplayName("Default comment is empty string")
    void testDefaultComment() {
      // Create Index without setting comment
      Index index = ImmutableIndex.builder().index(TEST_INDEX).fields(TEST_FIELDS).build();

      // Verify default comment
      assertEquals("", index.comment());
    }

    @Test
    @DisplayName("getId returns 0")
    void testGetId() {
      // Create Index
      Index index = ImmutableIndex.builder().index(TEST_INDEX).fields(TEST_FIELDS).build();

      int expectedCode = HashUtils.stringHash(index.index());
      assertEquals(expectedCode, index.code());
    }
  }
}
