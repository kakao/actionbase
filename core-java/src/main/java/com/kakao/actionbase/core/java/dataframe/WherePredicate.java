package com.kakao.actionbase.core.java.dataframe;

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
  @JsonSubTypes.Type(value = ImmutableWherePredicateIn.class, name = WherePredicate.IN),
  @JsonSubTypes.Type(value = ImmutableWherePredicateEq.class, name = WherePredicate.EQ),
  @JsonSubTypes.Type(value = ImmutableWherePredicateGt.class, name = WherePredicate.GT),
  @JsonSubTypes.Type(value = ImmutableWherePredicateGte.class, name = WherePredicate.GTE),
  @JsonSubTypes.Type(value = ImmutableWherePredicateLt.class, name = WherePredicate.LT),
  @JsonSubTypes.Type(value = ImmutableWherePredicateLte.class, name = WherePredicate.LTE),
  @JsonSubTypes.Type(value = ImmutableWherePredicateBetween.class, name = WherePredicate.BETWEEN),
  @JsonSubTypes.Type(value = ImmutableWherePredicateIsNull.class, name = WherePredicate.IS_NULL)
})
public interface WherePredicate {

  String IN = "in";
  String EQ = "eq";
  String GT = "gt";
  String GTE = "gte";
  String LT = "lt";
  String LTE = "lte";
  String BETWEEN = "bt";
  String IS_NULL = "is_null";

  @JsonIgnore
  String type();

  String key();

  static WherePredicate.In in(String key, List<Object> values) {
    return ImmutableWherePredicateIn.builder().key(key).values(values).build();
  }

  static WherePredicate.Eq eq(String key, Object value) {
    return ImmutableWherePredicateEq.builder().key(key).value(value).build();
  }

  static WherePredicate.Gt gt(String key, Object value) {
    return ImmutableWherePredicateGt.builder().key(key).value(value).build();
  }

  static WherePredicate.Gte gte(String key, Object value) {
    return ImmutableWherePredicateGte.builder().key(key).value(value).build();
  }

  static WherePredicate.Lt lt(String key, Object value) {
    return ImmutableWherePredicateLt.builder().key(key).value(value).build();
  }

  static WherePredicate.Lte lte(String key, Object value) {
    return ImmutableWherePredicateLte.builder().key(key).value(value).build();
  }

  static WherePredicate.Between between(String key, Object fromValue, Object toValue) {
    return ImmutableWherePredicateBetween.builder()
        .key(key)
        .fromValue(fromValue)
        .toValue(toValue)
        .build();
  }

  static WherePredicate.IsNull isNull(String key) {
    return ImmutableWherePredicateIsNull.builder().key(key).build();
  }

  default String toJson() throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.writeValueAsString(this);
  }

  static WherePredicate fromJson(String json) throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.readValue(json, WherePredicate.class);
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateIn.class)
  @JsonDeserialize(as = ImmutableWherePredicateIn.class)
  interface In extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.IN;
    }

    List<Object> values();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateEq.class)
  @JsonDeserialize(as = ImmutableWherePredicateEq.class)
  interface Eq extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.EQ;
    }

    Object value();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateGt.class)
  @JsonDeserialize(as = ImmutableWherePredicateGt.class)
  interface Gt extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.GT;
    }

    Object value();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateGte.class)
  @JsonDeserialize(as = ImmutableWherePredicateGte.class)
  interface Gte extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.GTE;
    }

    Object value();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateLt.class)
  @JsonDeserialize(as = ImmutableWherePredicateLt.class)
  interface Lt extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.LT;
    }

    Object value();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateLte.class)
  @JsonDeserialize(as = ImmutableWherePredicateLte.class)
  interface Lte extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.LTE;
    }

    Object value();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateBetween.class)
  @JsonDeserialize(as = ImmutableWherePredicateBetween.class)
  interface Between extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.BETWEEN;
    }

    Object fromValue();

    Object toValue();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableWherePredicate*")
  @JsonSerialize(as = ImmutableWherePredicateIsNull.class)
  @JsonDeserialize(as = ImmutableWherePredicateIsNull.class)
  interface IsNull extends WherePredicate {

    @Override
    @Value.Derived
    default String type() {
      return WherePredicate.IS_NULL;
    }
  }
}
