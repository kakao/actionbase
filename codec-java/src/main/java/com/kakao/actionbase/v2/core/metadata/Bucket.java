package com.kakao.actionbase.v2.core.metadata;

import com.kakao.actionbase.v2.core.types.DataType;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Ported from V3 {@code com.kakao.actionbase.core.metadata.common.Bucket}. Only the {@code date}
 * bucket is supported, and only {@link #apply(Object)} (bulk-encoding time bucketing) is ported —
 * {@code handleQueryValue} is query-time only and unused by bulk encoding.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = Bucket.Date.class, name = "date")})
public abstract class Bucket implements Serializable {

  public abstract String getName();

  public abstract Object apply(Object value);

  public static class Date extends Bucket {

    @JsonProperty("name")
    private final String name;

    @JsonProperty("unit")
    private final ValueUnit unit;

    @JsonProperty("timezone")
    private final String timezone;

    @JsonProperty("format")
    private final String format;

    @JsonIgnore private final transient ZoneId zoneId;
    @JsonIgnore private final transient DateTimeFormatter formatter;

    @JsonCreator
    public Date(
        @JsonProperty("name") String name,
        @JsonProperty("unit") ValueUnit unit,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("format") String format) {
      this.name = name;
      this.unit = unit;
      this.timezone = timezone;
      this.format = format;
      this.zoneId = ZoneId.of(timezone);
      this.formatter = DateTimeFormatter.ofPattern(format);
    }

    private Object readResolve() {
      return new Date(name, unit, timezone, format);
    }

    @Override
    public String getName() {
      return name;
    }

    public ValueUnit getUnit() {
      return unit;
    }

    public String getTimezone() {
      return timezone;
    }

    public String getFormat() {
      return format;
    }

    @Override
    public Object apply(Object value) {
      if (value == null) return null;

      try {
        long longValue = (Long) DataType.LONG.cast(value);

        Instant instant;
        switch (unit) {
          case NANOSECOND:
            instant = Instant.ofEpochSecond(0, longValue);
            break;
          case MICROSECOND:
            instant = Instant.ofEpochSecond(longValue / 1_000_000, (longValue % 1_000_000) * 1000);
            break;
          case MILLISECOND:
            instant = Instant.ofEpochMilli(longValue);
            break;
          case SECOND:
            instant = Instant.ofEpochSecond(longValue);
            break;
          default:
            throw new IllegalArgumentException("Unsupported unit: " + unit);
        }

        return instant.atZone(zoneId).format(formatter);
      } catch (Exception e) {
        return null;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Date)) return false;
      Date other = (Date) obj;
      return name.equals(other.name)
          && unit == other.unit
          && timezone.equals(other.timezone)
          && format.equals(other.format);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, unit, timezone, format);
    }

    @Override
    public String toString() {
      return "Date{"
          + "name='"
          + name
          + '\''
          + ", unit="
          + unit
          + ", timezone='"
          + timezone
          + '\''
          + ", format='"
          + format
          + '\''
          + '}';
    }
  }

  public enum ValueUnit {
    NANOSECOND,
    MICROSECOND,
    MILLISECOND,
    SECOND,
  }
}
