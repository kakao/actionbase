package com.kakao.actionbase.v2.core.types;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class VertexField implements Serializable {

  @JsonProperty private final VertexType type;

  @JsonProperty private final String desc;

  @JsonCreator
  public VertexField(
      @JsonProperty(value = "type", required = true) VertexType type,
      @JsonProperty(value = "desc") String desc) {
    this.type = type;
    if (desc != null) {
      this.desc = desc;
    } else {
      this.desc = "";
    }
  }

  public VertexField(VertexType type) {
    this(type, "");
  }

  public VertexType getType() {
    return type;
  }

  @JsonIgnore
  public DataType getDataType() {
    return type.getType();
  }

  public String getDesc() {
    return desc;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof VertexField) {
      VertexField other = (VertexField) obj;
      return type.equals(other.type) && desc.equals(other.desc);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, desc);
  }

  @Override
  public String toString() {
    return "VertexField{" + "type=" + type + ", desc='" + desc + '\'' + '}';
  }
}
