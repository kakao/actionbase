package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.Order;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 *   "cache": "permission_created_at_desc",
 *   "fields": [
 *     {"field": "permission", "order": "ASC", "dimension": ["me", "others"]},
 *     {"field": "created_at", "order": "DESC"}
 *   ],
 *   "limit": 100,
 *   "comment": "..."
 * }
 * </pre>
 *
 * <p>The cache field uses the {@code "field"} JSON key (not {@code "name"}) to align with V3 {@code
 * IndexField.kt}. {@link Index.Field} keeps the legacy {@code "name"} key, so cache fields must not
 * reuse it.
 *
 * <p>{@code dimension} is an optional whitelist: when set, only edges whose value for the field is
 * in the list are written to the cache. Other edges are silently skipped at write time.
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

  @JsonIgnore private final List<Field> dimensionedFields;

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
    this.dimensionedFields =
        Collections.unmodifiableList(
            this.fields.stream().filter(Field::hasDimension).collect(Collectors.toList()));
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

  public List<Field> getDimensionedFields() {
    return dimensionedFields;
  }

  public boolean hasAnyDimension() {
    return !dimensionedFields.isEmpty();
  }

  public static class Field implements Serializable {

    @JsonProperty("field")
    private final String field;

    @JsonProperty("order")
    private final Order order;

    @JsonProperty("dimension")
    private final Set<Object> dimension;

    @JsonCreator
    public Field(
        @JsonProperty("field") String field,
        @JsonProperty("order") Order order,
        @JsonProperty("dimension") Set<Object> dimension) {
      this.field = field;
      this.order = order;
      this.dimension = dimension == null ? null : new HashSet<>(dimension);
    }

    public Field(String field, Order order) {
      this(field, order, null);
    }

    public String getField() {
      return field;
    }

    public Order getOrder() {
      return order;
    }

    public Set<Object> getDimension() {
      return dimension;
    }

    public boolean hasDimension() {
      return dimension != null && !dimension.isEmpty();
    }

    @Override
    public String toString() {
      return "Field{"
          + "field='"
          + field
          + '\''
          + ", order="
          + order
          + ", dimension="
          + dimension
          + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Field) {
        Field other = (Field) obj;
        return Objects.equals(field, other.field)
            && Objects.equals(order, other.order)
            && Objects.equals(dimension, other.dimension);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, order, dimension);
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
