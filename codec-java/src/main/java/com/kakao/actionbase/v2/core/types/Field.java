package com.kakao.actionbase.v2.core.types;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Field implements Serializable {

  @JsonProperty final String name;

  @JsonProperty final DataType type;

  @JsonProperty final boolean nullable;

  final String desc;

  @JsonCreator
  public Field(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "type", required = true) DataType type,
      @JsonProperty(value = "nullable", required = true) boolean nullable,
      @JsonProperty(value = "desc") String desc) {
    this.name = name;
    this.type = type;
    this.nullable = nullable;
    if (desc != null) {
      this.desc = desc;
    } else {
      this.desc = "";
    }
  }

  public Field(String name, DataType type, boolean nullable) {
    this(name, type, nullable, "");
  }

  public String getName() {
    return name;
  }

  public DataType getType() {
    return type;
  }

  public boolean isNullable() {
    return nullable;
  }

  public String getDesc() {
    return desc;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Field) {
      Field other = (Field) obj;
      return name.equals(other.name)
          && type.equals(other.type)
          && nullable == other.nullable
          && desc.equals(other.desc);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, nullable, desc);
  }

  @Override
  public String toString() {
    return "Field{"
        + "name='"
        + name
        + '\''
        + ", type="
        + type
        + ", nullable="
        + nullable
        + ", desc='"
        + desc
        + '\''
        + '}';
  }
}
