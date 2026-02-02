package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.Order;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Index implements Serializable {

  @JsonIgnore final int id;

  @JsonProperty("name")
  final String name;

  @JsonProperty("fields")
  final List<Field> fields;

  @JsonProperty("desc")
  final String desc;

  @JsonCreator
  public Index(
      @JsonProperty("name") String name,
      @JsonProperty("fields") List<Field> fields,
      @JsonProperty("desc") String desc) {
    this.id = ValueUtils.stringHash(name);
    this.name = name;
    this.fields = fields;
    if (desc != null) {
      this.desc = desc;
    } else {
      this.desc = "";
    }
  }

  public Index(String name, List<Field> fields) {
    this(name, fields, "");
  }

  public String getDesc() {
    return desc;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<Field> getFields() {
    return fields;
  }

  public static class Field implements Serializable {

    @JsonProperty("name")
    final String name;

    @JsonProperty("order")
    final Order order;

    @JsonCreator
    public Field(@JsonProperty("name") String name, @JsonProperty("order") Order order) {
      this.name = name;
      this.order = order;
    }

    public String getName() {
      return name;
    }

    public Order getOrder() {
      return order;
    }

    @Override
    public String toString() {
      return "Field{" + "name='" + name + '\'' + ", order=" + order + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Field) {
        Field other = (Field) obj;
        return name.equals(other.name) && order.equals(other.order);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, order);
    }
  }

  @Override
  public String toString() {
    return "Index{" + "id=" + id + ", name='" + name + '\'' + ", fields=" + fields + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Index) {
      Index other = (Index) obj;
      return id == other.id
          && name.equals(other.name)
          && fields.equals(other.fields)
          && desc.equals(other.desc);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, fields, desc);
  }
}
