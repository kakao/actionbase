package com.kakao.actionbase.core.java.metadata.v2.common;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.constant.Constant;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableIndex.class)
@JsonDeserialize(as = ImmutableIndex.class)
public interface Index {

  IndexField DEFAULT_TIMESTAMP_INDEX_FIELD = ImmutableIndexField.of("ts", Order.DESC);

  String name();

  List<IndexField> fields();

  @Value.Default
  default String desc() {
    return Constant.DEFAULT_COMMENT;
  }

  default com.kakao.actionbase.core.java.metadata.v3.common.Index toV3() {
    // v2 allows empty index fields, but v3 does not.
    // fill with default timestamp index field for compatibility
    List<com.kakao.actionbase.core.java.metadata.v3.common.IndexField> newFields =
        fields().isEmpty()
            ? Collections.singletonList(DEFAULT_TIMESTAMP_INDEX_FIELD.toV3())
            : fields().stream().map(IndexField::toV3).collect(Collectors.toList());

    com.kakao.actionbase.core.java.metadata.v3.common.ImmutableIndex.Builder builder =
        com.kakao.actionbase.core.java.metadata.v3.common.ImmutableIndex.builder()
            .index(name())
            .fields(newFields);

    if (desc() != null) {
      builder.comment(desc());
    }

    return builder.build();
  }
}
