package com.kakao.actionbase.core.java.metadata.v3.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum DirectionType {
  BOTH(Direction.OUT, Direction.IN),
  OUT(Direction.OUT),
  IN(Direction.IN),
  ;

  private final List<Direction> directions;

  DirectionType(Direction... dirs) {
    this.directions = new ArrayList<>(dirs.length);
    this.directions.addAll(Arrays.asList(dirs));
  }

  public static DirectionType of(String name) {
    return DirectionType.valueOf(name.toUpperCase());
  }

  public List<Direction> directions() {
    return directions;
  }
}
