package com.kakao.actionbase.core.java.hbase;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableSizeMetric.class)
@JsonDeserialize(as = ImmutableSizeMetric.class)
public interface SizeMetric {

  SizeMetricUnit unit();

  long longValue();

  @Value.Auxiliary
  default SizeMetric add(SizeMetric other) {
    long sum = longValueInBytes() + other.longValueInBytes();
    switch (unit()) {
      case BYTE:
        return ImmutableSizeMetric.builder().unit(unit()).longValue(sum).build();
      case KILOBYTE:
        return ImmutableSizeMetric.builder().unit(unit()).longValue(sum / 1024L).build();
      case MEGABYTE:
        return ImmutableSizeMetric.builder().unit(unit()).longValue(sum / (1024L * 1024L)).build();
      case GIGABYTE:
        return ImmutableSizeMetric.builder()
            .unit(unit())
            .longValue(sum / (1024L * 1024L * 1024L))
            .build();
      default:
        throw new IllegalArgumentException("Unknown unit: " + unit());
    }
  }

  @Value.Auxiliary
  default long longValueInBytes() {
    long bytes;
    switch (unit()) {
      case BYTE:
        bytes = longValue();
        break;
      case KILOBYTE:
        bytes = longValue() * 1024L;
        break;
      case MEGABYTE:
        bytes = longValue() * 1024L * 1024L;
        break;
      case GIGABYTE:
        bytes = longValue() * 1024L * 1024L * 1024L;
        break;
      default:
        throw new IllegalArgumentException("Unknown unit: " + unit());
    }
    return bytes;
  }

  @Value.Auxiliary
  static SizeMetric empty(SizeMetricUnit unit) {
    return ImmutableSizeMetric.builder().unit(unit).longValue(0L).build();
  }
}
