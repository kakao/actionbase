package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.edge.Edges;
import com.kakao.actionbase.core.java.metadata.v3.common.*;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.common.ImmutableField;
import com.kakao.actionbase.core.java.types.common.ImmutableStructField;

import java.util.Collections;

public class Descriptors {

  public static final String TENANT_FIELD = "tenant";

  public static final String DATABASE_FIELD = "database";

  public static final String COMMENT_FIELD = "comment";

  public static class Schema {

    public static final EdgeSchema DATABASE =
        ImmutableEdgeSchema.builder()
            .source(ImmutableField.of(DataType.STRING, TENANT_FIELD))
            .target(ImmutableField.of(DataType.STRING, DATABASE_FIELD))
            .addProperties(ImmutableStructField.of(COMMENT_FIELD, DataType.STRING))
            .direction(DirectionType.OUT)
            .addIndexes(
                ImmutableIndex.builder()
                    .index(Edges.DEFAULT_INDEX_NAME)
                    .fields(
                        Collections.singletonList(
                            ImmutableIndexField.of(Edges.TARGET_FIELD, Order.ASC)))
                    .build())
            .build();
  }
}
