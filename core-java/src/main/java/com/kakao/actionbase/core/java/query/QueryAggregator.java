package com.kakao.actionbase.core.java.query;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;

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
  @JsonSubTypes.Type(
      value = ImmutableQueryAggregatorFlatten.class,
      name = QueryAggregator.FLATTEN_TYPE),
  @JsonSubTypes.Type(
      value = ImmutableQueryAggregatorCount.class,
      name = QueryAggregator.COUNT_TYPE),
  @JsonSubTypes.Type(value = ImmutableQueryAggregatorSum.class, name = QueryAggregator.SUM_TYPE)
})
public interface QueryAggregator {

  String FLATTEN_TYPE = "flatten";
  String COUNT_TYPE = "count";
  String SUM_TYPE = "sum";

  @JsonIgnore
  String type();

  static QueryAggregator flatten() {
    return Flatten.INSTANCE;
  }

  static ImmutableQueryAggregatorCount count(String field, Order order, int limit) {
    return ImmutableQueryAggregatorCount.builder().field(field).order(order).limit(limit).build();
  }

  static ImmutableQueryAggregatorSum sum(
      String valueField, List<String> keyFields, Order order, int limit) {
    return ImmutableQueryAggregatorSum.builder()
        .valueField(valueField)
        .keyFields(keyFields)
        .order(order)
        .limit(limit)
        .build();
  }

  default String toJson() throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.writeValueAsString(this);
  }

  static QueryAggregator fromJson(String json) throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.readValue(json, QueryAggregator.class);
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryAggregator*")
  @JsonSerialize(as = ImmutableQueryAggregatorFlatten.class)
  @JsonDeserialize(as = ImmutableQueryAggregatorFlatten.class)
  interface Flatten extends QueryAggregator {

    @Override
    @Value.Derived
    default String type() {
      return QueryAggregator.FLATTEN_TYPE;
    }

    Flatten INSTANCE = ImmutableQueryAggregatorFlatten.builder().build();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryAggregator*")
  @JsonSerialize(as = ImmutableQueryAggregatorCount.class)
  @JsonDeserialize(as = ImmutableQueryAggregatorCount.class)
  interface Count extends QueryAggregator {

    @Override
    @Value.Derived
    default String type() {
      return QueryAggregator.COUNT_TYPE;
    }

    String field();

    Order order();

    int limit();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryAggregator*")
  @JsonSerialize(as = ImmutableQueryAggregatorSum.class)
  @JsonDeserialize(as = ImmutableQueryAggregatorSum.class)
  interface Sum extends QueryAggregator {

    @Override
    @Value.Derived
    default String type() {
      return QueryAggregator.SUM_TYPE;
    }

    String valueField();

    List<String> keyFields();

    Order order();

    int limit();
  }
}
