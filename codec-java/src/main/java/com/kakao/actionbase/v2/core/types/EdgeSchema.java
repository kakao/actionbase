package com.kakao.actionbase.v2.core.types;

import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EdgeSchema implements Serializable {

  @JsonProperty("src")
  final VertexField src;

  @JsonProperty("tgt")
  final VertexField tgt;

  @JsonProperty("fields")
  final List<Field> fields;

  @JsonIgnore final Map<String, Field> nameToField;
  @JsonIgnore private final Map<Integer, String> hashToFieldNameMap;
  @JsonIgnore private final StructType allStructType;
  @JsonIgnore private final StructType structType;
  @JsonIgnore private final StructType edgeIdStructType;
  @JsonIgnore private final StructType offsetStructType;
  @JsonIgnore private final Map<String, Integer> fieldIndices;
  @JsonIgnore
  private final int edgeIdIndex, activeIndex, dirIndex, tsIndex, srcIndex, tgtIndex, offsetIndex;

  @JsonCreator
  public EdgeSchema(
      @JsonProperty("src") VertexField src,
      @JsonProperty("tgt") VertexField tgt,
      @JsonProperty("fields") List<Field> fields) {
    this.src = src;
    this.tgt = tgt;
    this.fields =
        Collections.unmodifiableList(new ArrayList<>(fields)); // Make fields list immutable

    this.nameToField =
        fields.stream().collect(Collectors.toMap(Field::getName, Function.identity()));

    this.hashToFieldNameMap =
        Collections.unmodifiableMap(
            fields.stream()
                .collect(
                    Collectors.toMap(
                        field -> ValueUtils.stringHash(field.getName()), Field::getName)));

    List<Field> fieldList = new ArrayList<>();
    fieldList.add(new Field(Fields.DIR, DataType.STRING, false, "direction"));
    fieldList.add(new Field(Fields.TS, DataType.LONG, false, "ts"));
    fieldList.add(new Field(Fields.SRC, src.getDataType(), false, src.getDesc()));
    fieldList.add(new Field(Fields.TGT, tgt.getDataType(), false, tgt.getDesc()));
    fieldList.addAll(fields);
    Field[] defaultFieldArray = fieldList.toArray(new Field[0]);
    this.structType = new StructType(defaultFieldArray);

    fieldList.add(new Field(Fields.EDGE_ID, DataType.STRING, false, "edgeId"));

    this.edgeIdStructType = new StructType(fieldList.toArray(new Field[0]));

    fieldList.add(new Field(Fields.ACTIVE, DataType.BOOLEAN, false, "active"));

    Field[] fieldsArray = fieldList.toArray(new Field[0]);
    this.allStructType = new StructType(fieldsArray);

    this.fieldIndices =
        Collections.unmodifiableMap(
            fields.stream()
                .collect(
                    Collectors.toMap(
                        Field::getName, field -> allStructType.fieldIndex(field.getName()))));

    this.edgeIdIndex = allStructType.fieldIndex(Fields.EDGE_ID);

    this.activeIndex = allStructType.fieldIndex(Fields.ACTIVE);

    this.dirIndex = allStructType.fieldIndex(Fields.DIR);

    this.tsIndex = allStructType.fieldIndex(Fields.TS);

    this.srcIndex = allStructType.fieldIndex(Fields.SRC);

    this.tgtIndex = allStructType.fieldIndex(Fields.TGT);

    // temporary implementation for offset
    List<Field> offsetFieldList = new ArrayList<>();
    offsetFieldList.add(new Field(Fields.DIR, DataType.STRING, false, "direction"));
    offsetFieldList.add(new Field(Fields.TS, DataType.LONG, false, "ts"));
    offsetFieldList.add(new Field(Fields.SRC, src.getDataType(), false, src.getDesc()));
    offsetFieldList.add(new Field(Fields.TGT, tgt.getDataType(), false, tgt.getDesc()));
    offsetFieldList.addAll(fields);
    offsetFieldList.add(new Field(Fields.OFFSET, DataType.STRING, false, "offset"));

    this.offsetStructType = new StructType(offsetFieldList.toArray(new Field[0]));

    this.offsetIndex = offsetStructType.fieldIndex(Fields.OFFSET);
  }

  public VertexField getSrc() {
    return src;
  }

  public VertexField getTgt() {
    return tgt;
  }

  public List<Field> getFields() {
    return fields;
  }

  public Field getField(String name) {
    return nameToField.get(name);
  }

  public StructType getAllStructType() {
    return allStructType;
  }

  public StructType getEdgeIdStructType() {
    return edgeIdStructType;
  }

  public StructType getOffsetStructType() {
    return offsetStructType;
  }

  public StructType getStructType() {
    return structType;
  }

  public Map<Integer, String> getHashToFieldNameMap() {
    return hashToFieldNameMap;
  }

  public int getEdgeIdIndex() {
    return edgeIdIndex;
  }

  public int getActiveIndex() {
    return activeIndex;
  }

  public int getDirIndex() {
    return dirIndex;
  }

  public int getTsIndex() {
    return tsIndex;
  }

  public int getSrcIndex() {
    return srcIndex;
  }

  public int getTgtIndex() {
    return tgtIndex;
  }

  public int getOffsetIndex() {
    return offsetIndex;
  }

  public Integer getFieldIndex(String name) {
    return fieldIndices.get(name);
  }

  public EdgeSchema copy(VertexField tgt) {
    return new EdgeSchema(src, tgt, fields);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EdgeSchema)) return false;
    EdgeSchema that = (EdgeSchema) o;
    return src.equals(that.src) && tgt.equals(that.tgt) && fields.equals(that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(src, tgt, fields);
  }

  @Override
  public String toString() {
    return "EdgeSchema{" + "src=" + src + ", tgt=" + tgt + ", fields=" + fields + '}';
  }

  public static class Fields {
    public static final String EDGE_ID = "edgeId";
    public static final String ACTIVE = "active";
    public static final String DIR = "dir";
    public static final String TS = "ts";
    public static final String SRC = "src";
    public static final String TGT = "tgt";
    public static final String OFFSET = "offset";
  }
}
