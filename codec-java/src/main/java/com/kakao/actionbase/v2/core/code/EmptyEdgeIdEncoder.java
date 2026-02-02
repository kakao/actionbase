package com.kakao.actionbase.v2.core.code;

public class EmptyEdgeIdEncoder implements IdEdgeEncoder {

  public static final EmptyEdgeIdEncoder INSTANCE = new EmptyEdgeIdEncoder();

  private EmptyEdgeIdEncoder() {}

  @Override
  public String encode(Object src, Object tgt) {
    return null;
  }

  @Override
  public KeyValue<Object> decode(String edgeId) {
    return null;
  }
}
