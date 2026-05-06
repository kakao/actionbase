package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.Order;
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
 *   "fields": [{"field": "created_at", "order": "DESC"}],
 *   "limit": 100,
 *   "comment": "..."
 * }
 * </pre>
 *
 * <p>The cache field uses the {@code "field"} JSON key (not {@code "name"}) to align with V3 {@code
 * IndexField.kt}. {@link Index.Field} keeps the legacy {@code "name"} key, so cache fields must not
 * reuse it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cache implements Serializable {

  public static final int DEFAULT_LIMIT = 100;
  public static final String DEFAULT_COMMENT = "";

  @JsonProperty("cache")
  private final String cache;

  @JsonProperty("fields")
  private final List<Field> fields;

  @JsonProperty("limit")
  private final int limit;

  @JsonProperty("comment")
  private final String comment;

  @JsonIgnore private final int code;

  @JsonCreator
  public Cache(
      @JsonProperty("cache") String cache,
      @JsonProperty("fields") List<Field> fields,
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

  public Cache(String cache, List<Field> fields) {
    this(cache, fields, null, null);
  }

  public String getCache() {
    return cache;
  }

  public List<Field> getFields() {
    return fields;
  }

  public static class Field implements Serializable {

    @JsonProperty("field")
    private final String field;

    @JsonProperty("order")
    private final Order order;

    @JsonCreator
    public Field(@JsonProperty("field") String field, @JsonProperty("order") Order order) {
      this.field = field;
      this.order = order;
    }

    public String getField() {
      return field;
    }

    public Order getOrder() {
      return order;
    }

    @Override
    public String toString() {
      return "Field{" + "field='" + field + '\'' + ", order=" + order + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Field) {
        Field other = (Field) obj;
        return Objects.equals(field, other.field) && Objects.equals(order, other.order);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, order);
    }
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
