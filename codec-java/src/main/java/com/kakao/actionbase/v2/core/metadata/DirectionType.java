package com.kakao.actionbase.v2.core.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum DirectionType {
  BOTH(Direction.OUT, Direction.IN),
  OUT(Direction.OUT),
  IN(Direction.IN),
  ;

  private final List<Direction> dirs;

  DirectionType(Direction... dirs) {
    this.dirs = new ArrayList<>(dirs.length);
    this.dirs.addAll(Arrays.asList(dirs));
  }

  public static DirectionType of(String name) {
    return DirectionType.valueOf(name.toUpperCase());
  }

  public List<Direction> getDirs() {
    return dirs;
  }
}
