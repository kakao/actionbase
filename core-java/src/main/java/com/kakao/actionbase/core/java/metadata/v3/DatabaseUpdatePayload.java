package com.kakao.actionbase.core.java.metadata.v3;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableDatabaseUpdatePayload.class)
@JsonDeserialize(as = ImmutableDatabaseUpdatePayload.class)
public interface DatabaseUpdatePayload {

  String comment();

  default void check() {}
}
