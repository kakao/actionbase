package com.kakao.actionbase.core.java.pipeline;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.compat.v2.Audit;
import com.kakao.actionbase.core.java.compat.v2.EdgeOperation;
import com.kakao.actionbase.core.java.compat.v2.ImmutableAudit;
import com.kakao.actionbase.core.java.compat.v2.TraceableEdge;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(
    value = {"version"},
    allowGetters = true)
public interface MessageV2 extends Message {

  @Override
  @Value.Derived
  default String version() {
    return MessageConstants.VERSION_V2;
  }

  // processing time
  long ts();

  String tenant();

  String phase();

  @Nullable
  String alias();

  String label();

  TraceableEdge edge();

  EdgeOperation op();

  @Value.Default
  default Audit audit() {
    return ImmutableAudit.builder().actor("default").build();
  }

  @Value.Default
  default String requestId() {
    return "";
  }

  @Override
  @JsonIgnore
  @Value.Auxiliary
  default byte[] key() {
    String partitionKey = edge().src() + ":" + edge().tgt();
    return partitionKey.getBytes();
  }
}
