package com.kakao.actionbase.v2.core.metadata;

import com.kakao.actionbase.v2.core.code.Index;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;
import com.kakao.actionbase.v2.core.types.EdgeSchema;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LabelDTO implements Serializable {

  @JsonProperty("name")
  final String name;

  @JsonProperty("desc")
  final String desc;

  @JsonProperty("type")
  final LabelType type;

  @JsonProperty("schema")
  final EdgeSchema schema;

  @JsonProperty("dirType")
  final DirectionType dirType;

  @JsonProperty("storage")
  final String storage;

  @JsonProperty("indices")
  final List<Index> indices;

  @JsonProperty("groups")
  final List<Group> groups;

  @JsonProperty("event")
  final boolean event;

  @JsonProperty("readOnly")
  final boolean readOnly;

  @JsonProperty("mode")
  final MutationMode mode;

  @JsonIgnore final int id;

  @JsonCreator
  public LabelDTO(
      @JsonProperty("name") String name,
      @JsonProperty("desc") String desc,
      @JsonProperty("type") LabelType type,
      @JsonProperty("schema") EdgeSchema schema,
      @JsonProperty("dirType") DirectionType dirType,
      @JsonProperty("storage") String storage,
      @JsonProperty("indices") List<Index> indices,
      @JsonProperty("groups") List<Group> groups,
      @JsonProperty("event") boolean event,
      @JsonProperty("readOnly") boolean readOnly,
      @JsonProperty("mode") MutationMode mode) {
    this.name = name;
    this.desc = desc;
    this.type = type;
    this.schema = schema;
    this.dirType = dirType;
    this.storage = storage;
    this.indices = indices;
    this.groups = groups;
    this.event = event;
    this.readOnly = readOnly;
    this.mode = mode;

    this.id = ValueUtils.stringHash(name);
  }

  public LabelDTO copy(String name, String storage) {
    return new LabelDTO(
        name, desc, type, schema, dirType, storage, indices, groups, event, readOnly, mode);
  }

  public String getName() {
    return name;
  }

  public String getDesc() {
    return desc;
  }

  public LabelType getType() {
    return type;
  }

  public EdgeSchema getSchema() {
    return schema;
  }

  public DirectionType getDirType() {
    return dirType;
  }

  public String getStorage() {
    return storage;
  }

  public List<Index> getIndices() {
    return indices;
  }

  public List<Group> getGroups() {
    return groups;
  }

  public boolean isEvent() {
    return event;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public MutationMode getMode() {
    return mode;
  }

  public int getId() {
    return id;
  }
}
