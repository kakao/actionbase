package com.kakao.actionbase.v2.core.types;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StructType implements Collection<Field>, Serializable {

  private final Field[] fields;

  private final String[] fieldNames;

  private Set<String> fieldNamesSet;

  private Map<String, Field> nameToField;

  private Map<String, Integer> nameToIndex;

  private Integer hashCode;

  public StructType(Field[] fields) {
    this.fields = fields;
    this.fieldNames = Arrays.stream(fields).map(Field::getName).toArray(String[]::new);
    this.nameToField =
        Collections.unmodifiableMap(
            Arrays.stream(fields).collect(Collectors.toMap(Field::getName, Function.identity())));
  }

  public StructType() {
    this(new Field[] {});
  }

  public Field[] getFields() {
    return fields;
  }

  public Field getField(String name) {
    return nameToField.get(name);
  }

  public String[] getFieldNames() {
    return fieldNames;
  }

  public Map<String, Field> getNameToField() {
    if (nameToField == null) {
      nameToField = new HashMap<>();
      for (Field field : fields) {
        nameToField.put(field.getName(), field);
      }
    }
    return nameToField;
  }

  private Map<String, Integer> getNameToIndex() {
    if (nameToIndex == null) {
      nameToIndex = new HashMap<>();
      String[] fieldNames = getFieldNames();
      for (int i = 0; i < fieldNames.length; i++) {
        nameToIndex.put(fieldNames[i], i);
      }
    }
    return nameToIndex;
  }

  @Override
  public int size() {
    return fields.length;
  }

  @Override
  public boolean containsAll(Collection<?> elements) {
    return elements.stream().allMatch(this::contains);
  }

  @Override
  public boolean addAll(Collection<? extends Field> c) {
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return false;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return false;
  }

  @Override
  public void clear() {}

  @Override
  public boolean contains(Object element) {
    return element instanceof Field && Arrays.asList(fields).contains(element);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof StructType)) return false;
    StructType that = (StructType) other;
    return Arrays.equals(this.fields, that.fields);
  }

  @Override
  public int hashCode() {
    if (hashCode == null) {
      hashCode = Arrays.hashCode(fields);
    }
    return hashCode;
  }

  @Override
  public boolean isEmpty() {
    return fields.length == 0;
  }

  @Override
  public Iterator<Field> iterator() {
    return Arrays.asList(fields).iterator();
  }

  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException("toArray is not supported");
  }

  @Override
  public <T> T[] toArray(T[] a) {
    throw new UnsupportedOperationException("toArray is not supported");
  }

  @Override
  public boolean add(Field field) {
    throw new UnsupportedOperationException("toArray is not supported");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("toArray is not supported");
  }

  public int fieldIndex(String name) {
    Integer index = getNameToIndex().get(name);
    if (index == null) {
      throw new IllegalArgumentException(
          name + " does not exist. Available: " + String.join(", ", getFieldNames()));
    }
    return index;
  }

  public String schemaString(boolean child) {
    return child
        ? "struct<"
            + Arrays.stream(fields)
                .map(field -> field.getName() + ": " + field.getType())
                .collect(Collectors.joining(","))
            + ">"
        : Arrays.stream(fields)
            .map(field -> field.getName() + " " + field.getType())
            .collect(Collectors.joining(", "));
  }
}
