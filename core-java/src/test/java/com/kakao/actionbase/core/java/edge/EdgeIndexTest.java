package com.kakao.actionbase.core.java.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.edge.index.EdgeIndexes;
import com.kakao.actionbase.core.java.edge.index.EncodableEdgeIndex;
import com.kakao.actionbase.core.java.metadata.v3.EdgeTableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.ImmutableEdgeTableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.ImmutableEdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.ImmutableIndexField;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.ImmutableField;
import com.kakao.actionbase.test.AbstractUnitTest;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EdgeIndexTest extends AbstractUnitTest<EdgeState> {

  EdgeTableDescriptor table;

  protected EdgeState createTarget() {
    StructType schema =
        ImmutableStructType.builder()
            .addField("prop1", DataType.STRING)
            .addField("prop2", DataType.LONG)
            .build();

    EdgeSchema edgeSchema =
        ImmutableEdgeSchema.builder()
            .source(ImmutableField.builder().type(DataType.STRING).build())
            .target(ImmutableField.builder().type(DataType.STRING).build())
            .properties(schema.fields())
            .addIndex("index1", ImmutableIndexField.of("prop1", Order.DESC))
            .addIndex(
                "index2",
                ImmutableIndexField.of("prop2", Order.DESC),
                ImmutableIndexField.of("version", Order.DESC))
            .direction(DirectionType.BOTH)
            .build();

    table =
        ImmutableEdgeTableDescriptor.builder()
            .active(true)
            .tenant("test-tenant")
            .database("test-database")
            .table("test-table")
            .storage("test-storage")
            .schema(edgeSchema)
            .mode(MutationMode.SYNC)
            .build();

    return ImmutableEdgePayload.builder()
        .version(123)
        .source("sourceId")
        .target("targetId")
        .putProperties("prop1", "value1")
        .putProperties("prop2", 42)
        .build()
        .toEvent(EventType.INSERT)
        .toState(schema);
  }

  @BeforeEach
  protected void setUp() {
    testTarget = createTarget();
  }

  @Test
  void testToEdgeIndexes() {
    // given & when
    List<EncodableEdgeIndex> edgeIndexes = EdgeIndexes.build(testTarget, table);

    // then
    assertEquals(4, edgeIndexes.size());
    for (EncodableEdgeIndex index : edgeIndexes) {
      assertEquals(testTarget.version(), index.version());
      assertEquals(table.code(), index.tableCode());

      // Verify source/target based on direction
      if (index.direction() == Direction.OUT) {
        assertEquals(testTarget.source(), index.directedSource());
        assertEquals(testTarget.target(), index.directedTarget());
      } else {
        assertEquals(testTarget.target(), index.directedSource());
        assertEquals(testTarget.source(), index.directedTarget());
      }

      // Verify properties
      assertEquals(2, index.properties().size());
      assertEquals("value1", index.properties().get("prop1"));
      assertEquals(42, index.properties().get("prop2"));

      // Verify index code
      int indexCode = index.indexCode();
      assertTrue(
          indexCode == table.schema().indexes().get(0).code()
              || indexCode == table.schema().indexes().get(1).code());

      if (indexCode == table.schema().indexes().get(0).code()) {
        assertEquals(1, index.indexValues().size()); // prop1
      } else {
        assertEquals(2, index.indexValues().size()); // prop2, version
      }
    }
  }
}
