package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.types.DataType;

import java.util.HashMap;
import java.util.Map;

public class Edges {

  private Edges() {}

  public static Edge fromMap(Map<String, Object> originalMap) {
    long ts = DataType.LONG.cast(originalMap.get("ts"));
    Object src = originalMap.get("src").toString();
    Object tgt = originalMap.get("tgt").toString();

    // Move remaining fields to nested map
    Map<String, Object> props = new HashMap<>();
    originalMap.forEach(
        (key, value) -> {
          if (!key.equals("ts") && !key.equals("src") && !key.equals("tgt") && !key.equals("dir")) {
            props.put(key, value);
          }
        });

    return ImmutableEdge.builder().ts(ts).src(src).tgt(tgt).props(props).build();
  }
}
