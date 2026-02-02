package com.kakao.actionbase.core.java.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageConstants {

  public static final String VERSION_V2 = "2";
  public static final String VERSION_V3 = "3";
  public static final String EDGE_TYPE = "e";
  public static final String VERTEX_TYPE = "v";

  // Field names used in deserialization
  public static final String VERSION_FIELD = "version";
  public static final String TYPE_FIELD = "type";

  public static final ObjectMapper objectMapper = new ObjectMapper();

  private MessageConstants() {} // Prevent instantiation
}
