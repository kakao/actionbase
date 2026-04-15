package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines a cache specification used to build {@code EdgeCacheRecord} (wide row) for multi-hop
 * queries. The {@link #code} is derived at runtime by {@code XXHash32(cache)} with seed 0, which
 * matches {@code XXHash32Wrapper.default.stringHash(cache)} in V3.
 *
 * <p>JSON shape (compatible with V3 {@code Cache.kt}):
 *
 * <pre>
 * {
 *   "cache": "top_created_at",
 *   "fields": [{"name": "created_at", "order": "DESC"}],
 *   "limit": 100,
 *   "comment": "..."
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cache implements Serializable {

  public static final int DEFAULT_LIMIT = 100;
  public static final String DEFAULT_COMMENT = "";

  @JsonProperty("cache")
  private final String cache;

  @JsonProperty("fields")
  private final List<Index.Field> fields;

  @JsonProperty("limit")
  private final int limit;

  @JsonProperty("comment")
  private final String comment;

  @JsonIgnore private final int code;

  @JsonCreator
  public Cache(
      @JsonProperty("cache") String cache,
      @JsonProperty("fields") List<Index.Field> fields,
      @JsonProperty("limit") Integer limit,
      @JsonProperty("comment") String comment) {
    if (cache == null || cache.isEmpty()) {
      throw new IllegalArgumentException("Cache name must not be empty");
    }
    int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
    if (resolvedLimit <= 0) {
      throw new IllegalArgumentException("Cache limit must be positive, got: " + resolvedLimit);
    }
    this.cache = cache;
    this.fields = fields == null ? Collections.emptyList() : fields;
    this.limit = resolvedLimit;
    this.comment = comment == null ? DEFAULT_COMMENT : comment;
    this.code = ValueUtils.stringHash(cache);
  }

  public Cache(String cache, List<Index.Field> fields) {
    this(cache, fields, null, null);
  }

  public String getCache() {
    return cache;
  }

  public List<Index.Field> getFields() {
    return fields;
  }

  public int getLimit() {
    return limit;
  }

  public String getComment() {
    return comment;
  }

  public int getCode() {
    return code;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Cache)) return false;
    Cache other = (Cache) obj;
    return limit == other.limit
        && cache.equals(other.cache)
        && fields.equals(other.fields)
        && comment.equals(other.comment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cache, fields, limit, comment);
  }

  @Override
  public String toString() {
    return "Cache{cache='"
        + cache
        + "', fields="
        + fields
        + ", limit="
        + limit
        + ", comment='"
        + comment
        + "'}";
  }
}
