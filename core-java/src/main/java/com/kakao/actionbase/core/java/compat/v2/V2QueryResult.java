package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.payload.DataFrameMapPayload;
import com.kakao.actionbase.core.java.payload.ImmutableDataFrameMapPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public class V2QueryResult {

  private final List<Map<String, Object>> data;
  private final int rows;
  private final List<String> stats;
  private final String offset;
  private final boolean hasNext;

  public V2QueryResult(
      @JsonProperty("data") List<Map<String, Object>> data,
      @JsonProperty("rows") int rows,
      @JsonProperty("stats") List<String> stats,
      @JsonProperty("offset") String offset,
      @JsonProperty("hasNext") boolean hasNext) {
    this.data = data;
    this.rows = rows;
    this.stats = stats;
    this.offset = offset;
    this.hasNext = hasNext;
  }

  @JsonProperty("data")
  public List<Map<String, Object>> data() {
    return data;
  }

  @JsonProperty("rows")
  public int rows() {
    return rows;
  }

  @JsonProperty("stats")
  public List<String> stats() {
    return stats;
  }

  @JsonProperty("offset")
  public String offset() {
    return offset;
  }

  @JsonProperty("hasNext")
  public boolean hasNext() {
    return hasNext;
  }

  @Override
  public String toString() {
    return "V2QueryResult{"
        + "data="
        + data
        + ", rows="
        + rows
        + ", stats="
        + stats
        + ", offset='"
        + offset
        + '\''
        + ", hasNext="
        + hasNext
        + '}';
  }

  public DataFrameMapPayload toEdgeDataFrame() {
    return ImmutableDataFrameMapPayload.builder().data(data).build();
  }

  public DataFrameMapPayload toV3() {
    List<Map<String, Object>> transformedData =
        data.stream()
            .map(
                originalMap -> {
                  Map<String, Object> newMap = new HashMap<>();
                  String dir = (String) originalMap.get("dir");
                  Object source = originalMap.get("src");
                  Object target = originalMap.get("tgt");
                  if ("IN".equals(dir)) {
                    newMap.put("source", target);
                    newMap.put("target", source);
                  } else {
                    newMap.put("source", source);
                    newMap.put("target", target);
                  }
                  newMap.remove("ts");
                  newMap.remove("dir");

                  // Move remaining fields to nested map
                  Map<String, Object> nestedMap = new HashMap<>();
                  originalMap.forEach(
                      (key, value) -> {
                        if (!key.equals("ts")
                            && !key.equals("src")
                            && !key.equals("tgt")
                            && !key.equals("dir")) {
                          nestedMap.put(key, value);
                        }
                      });

                  if (!nestedMap.isEmpty()) {
                    newMap.put("properties", nestedMap);
                  }

                  return newMap;
                })
            .collect(Collectors.toList());

    return ImmutableDataFrameMapPayload.builder().data(transformedData).build();
  }
}
