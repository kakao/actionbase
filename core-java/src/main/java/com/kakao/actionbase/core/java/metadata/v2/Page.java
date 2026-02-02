package com.kakao.actionbase.core.java.metadata.v2;

import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.TenantId;

import java.util.List;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Page metadata model. A container that holds ActionbaseModel, used for pagination processing. */
@Value.Immutable
@JsonSerialize(as = ImmutablePage.class)
@JsonDeserialize(as = ImmutablePage.class)
public interface Page<
    V3 extends com.kakao.actionbase.core.java.metadata.v3.Descriptor<?>, T extends Descriptor<V3>> {

  int count();

  List<T> content();

  @Value.Auxiliary
  @JsonIgnore
  default String toJson() {
    return ActionbaseObjectMapper.toJson(this);
  }

  @JsonIgnore
  default boolean isEmpty() {
    return count() == 0 || content().isEmpty();
  }

  default com.kakao.actionbase.core.java.metadata.v3.Page<V3> toV3(TenantId tenant) {
    return com.kakao.actionbase.core.java.metadata.v3.ImmutablePage.of(
        content().stream().map(x -> x.toV3(tenant)).collect(Collectors.toList()));
  }
}
