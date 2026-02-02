package com.kakao.actionbase.core.java.pipeline;

import com.kakao.actionbase.core.java.pipeline.common.V3Context;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(
    value = {"version", "type"},
    allowGetters = true)
public interface MessageV3 extends Message {

  @Override
  @Value.Derived
  default String version() {
    return MessageConstants.VERSION_V3;
  }

  long processedAt();

  String type();

  V3Context context();
}
