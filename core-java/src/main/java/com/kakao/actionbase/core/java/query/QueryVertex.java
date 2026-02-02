package com.kakao.actionbase.core.java.query;

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
  @JsonSubTypes.Type(value = ImmutableQueryVertexConstant.class, name = QueryVertex.CONSTANT_TYPE),
  @JsonSubTypes.Type(value = ImmutableQueryVertexRef.class, name = QueryVertex.REF_TYPE),
})
public interface QueryVertex {

  String CONSTANT_TYPE = "constant";
  String REF_TYPE = "ref";
  String STEP_TYPE = "step";

  @JsonIgnore
  String type();

  static ImmutableQueryVertexConstant constant(Object... values) {
    return ImmutableQueryVertexConstant.builder().addValues(values).build();
  }

  static ImmutableQueryVertexRef ref(String ref, String field) {
    return ImmutableQueryVertexRef.builder().ref(ref).field(field).build();
  }

  static ImmutableQueryVertexStep step(QueryStep step, String field) {
    return ImmutableQueryVertexStep.builder().step(step).field(field).build();
  }

  default String toJson() throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.writeValueAsString(this);
  }

  static QueryVertex fromJson(String json) throws JsonProcessingException {
    return ActionbaseObjectMapper.INSTANCE.readValue(json, QueryVertex.class);
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryVertex*")
  @JsonSerialize(as = ImmutableQueryVertexConstant.class)
  @JsonDeserialize(as = ImmutableQueryVertexConstant.class)
  interface Constant extends QueryVertex {

    @Override
    @Value.Derived
    default String type() {
      return QueryVertex.CONSTANT_TYPE;
    }

    List<Object> values();
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryVertex*")
  @JsonSerialize(as = ImmutableQueryVertexRef.class)
  @JsonDeserialize(as = ImmutableQueryVertexRef.class)
  interface Ref extends QueryVertex {

    @Override
    @Value.Derived
    default String type() {
      return QueryVertex.REF_TYPE;
    }

    String ref();

    String field();
  }

  // should serialize using QueryNameRef with step.name() as ref
  @Value.Immutable
  @Value.Style(typeImmutable = "ImmutableQueryVertex*")
  interface Step extends QueryVertex {

    @Override
    @Value.Derived
    default String type() {
      return QueryVertex.STEP_TYPE;
    }

    QueryStep step();

    String field();
  }
}
