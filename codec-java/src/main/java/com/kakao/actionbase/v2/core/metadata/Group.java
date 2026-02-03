package com.kakao.actionbase.v2.core.metadata;

import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Group implements Serializable {

  @JsonIgnore final int code;

  @JsonProperty("group")
  final String group;

  @JsonProperty("type")
  final GroupType type;

  @JsonProperty("fields")
  final List<Field> fields;

  @JsonProperty("valueField")
  final String valueField;

  @JsonProperty("comment")
  final String comment;

  @JsonProperty("directionType")
  final DirectionType directionType;

  @JsonProperty("ttl")
  final long ttl;

  @JsonCreator
  public Group(
      @JsonProperty("group") String group,
      @JsonProperty("type") GroupType type,
      @JsonProperty("fields") List<Field> fields,
      @JsonProperty("valueField") String valueField,
      @JsonProperty("comment") String comment,
      @JsonProperty("directionType") DirectionType directionType,
      @JsonProperty("ttl") Long ttl) {
    this.code = ValueUtils.stringHash(group);
    this.group = group;
    this.type = type;
    this.fields = fields;
    this.valueField = valueField != null ? valueField : "-";
    this.comment = comment != null ? comment : "";
    this.directionType = directionType != null ? directionType : DirectionType.BOTH;
    this.ttl = ttl != null ? ttl : -1L;
  }

  public int getCode() {
    return code;
  }

  public String getGroup() {
    return group;
  }

  public GroupType getType() {
    return type;
  }

  public List<Field> getFields() {
    return fields;
  }

  public String getValueField() {
    return valueField;
  }

  public String getComment() {
    return comment;
  }

  public DirectionType getDirectionType() {
    return directionType;
  }

  public long getTtl() {
    return ttl;
  }

  public static class Field implements Serializable {

    @JsonProperty("name")
    final String name;

    @JsonCreator
    public Field(@JsonProperty("name") String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return "Field{" + "name='" + name + '\'' + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Field) {
        Field other = (Field) obj;
        return name.equals(other.name);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  @Override
  public String toString() {
    return "Group{"
        + "code="
        + code
        + ", group='"
        + group
        + '\''
        + ", type="
        + type
        + ", fields="
        + fields
        + ", valueField='"
        + valueField
        + '\''
        + ", comment='"
        + comment
        + '\''
        + ", directionType="
        + directionType
        + ", ttl="
        + ttl
        + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Group) {
      Group other = (Group) obj;
      return code == other.code
          && group.equals(other.group)
          && type == other.type
          && fields.equals(other.fields)
          && valueField.equals(other.valueField)
          && comment.equals(other.comment)
          && directionType == other.directionType
          && ttl == other.ttl;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, group, type, fields, valueField, comment, directionType, ttl);
  }
}
