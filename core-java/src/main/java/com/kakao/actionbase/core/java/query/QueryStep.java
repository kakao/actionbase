package com.kakao.actionbase.core.java.query;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.dataframe.WherePredicate;
import com.kakao.actionbase.core.java.exception.ActionbaseException;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;
import com.kakao.actionbase.core.java.util.ULID;

import java.util.Collections;
import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ImmutableQueryStepGet.class, name = QueryStep.GET_TYPE),
  @JsonSubTypes.Type(value = ImmutableQueryStepScan.class, name = QueryStep.SCAN_TYPE),
  @JsonSubTypes.Type(value = ImmutableQueryStepCount.class, name = QueryStep.COUNT_TYPE)
})
public interface QueryStep {

  String SELF_TYPE = "self";
  String GET_TYPE = "get";
  String SCAN_TYPE = "scan";
  String COUNT_TYPE = "count";

  @JsonIgnore
  String type();

  @Value.Default
  default String name() {
    return ULID.random();
  }

  @Value.Default
  default boolean include() {
    return false;
  }

  @Value.Default
  default boolean cache() {
    return false;
  }

  @Value.Default
  default List<QueryAggregator> aggregators() {
    return Collections.emptyList();
  }

  @Value.Default
  default List<WherePredicate> predicates() {
    return Collections.emptyList();
  }

  static ImmutableQueryStepGet.Builder get() {
    return ImmutableQueryStepGet.builder();
  }

  static ImmutableQueryStepSelf.Builder self() {
    return ImmutableQueryStepSelf.builder();
  }

  static ImmutableQueryStepScan.Builder scan() {
    return ImmutableQueryStepScan.builder();
  }

  static ImmutableQueryStepCount.Builder count() {
    return ImmutableQueryStepCount.builder();
  }

  default String toJson() throws ActionbaseException {
    try {
      return ActionbaseObjectMapper.INSTANCE.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("Failed to serialize QueryStep", e);
    }
  }

  static QueryStep fromJson(String json) throws ActionbaseException {
    try {
      return ActionbaseObjectMapper.INSTANCE.readValue(json, QueryStep.class);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("Failed to deserialize QueryStep", e);
    }
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryStep*")
  @JsonSerialize(as = ImmutableQueryStepSelf.class)
  @JsonDeserialize(as = ImmutableQueryStepSelf.class)
  interface Self extends QueryStep {

    @Override
    @Value.Derived
    default String type() {
      return QueryStep.SELF_TYPE;
    }

    String database();

    String table();

    QueryVertex source();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryStep*")
  @JsonSerialize(as = ImmutableQueryStepGet.class)
  @JsonDeserialize(as = ImmutableQueryStepGet.class)
  interface Get extends QueryStep {

    @Override
    @Value.Derived
    default String type() {
      return QueryStep.GET_TYPE;
    }

    String database();

    String table();

    QueryVertex source();

    QueryVertex target();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryStep*")
  @JsonSerialize(as = ImmutableQueryStepScan.class)
  @JsonDeserialize(as = ImmutableQueryStepScan.class)
  interface Scan extends QueryStep {

    @Override
    @Value.Derived
    default String type() {
      return QueryStep.SCAN_TYPE;
    }

    String database();

    String table();

    QueryVertex start();

    Direction direction();

    String index();

    @Nullable
    Integer limit();

    @Nullable
    String offset();

    default Scan withConstantStart(Object value) {
      return ImmutableQueryStepScan.copyOf(this).withStart(QueryVertex.constant(value));
    }
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryStep*")
  @JsonSerialize(as = ImmutableQueryStepCount.class)
  @JsonDeserialize(as = ImmutableQueryStepCount.class)
  interface Count extends QueryStep {

    @Override
    @Value.Derived
    default String type() {
      return QueryStep.COUNT_TYPE;
    }

    String database();

    String table();

    QueryVertex start();

    Direction direction();
  }
}
