package com.kakao.actionbase.core.java.metadata.v2.common;

public enum MutationMode {
  SYNC(com.kakao.actionbase.core.java.metadata.v3.common.MutationMode.SYNC),
  ASYNC(com.kakao.actionbase.core.java.metadata.v3.common.MutationMode.ASYNC),
  IGNORE(com.kakao.actionbase.core.java.metadata.v3.common.MutationMode.DROP);

  private final com.kakao.actionbase.core.java.metadata.v3.common.MutationMode mutationMode;

  MutationMode(com.kakao.actionbase.core.java.metadata.v3.common.MutationMode mutationMode) {
    this.mutationMode = mutationMode;
  }

  public com.kakao.actionbase.core.java.metadata.v3.common.MutationMode getMutationMode(
      boolean readOnly) {
    if (readOnly) {
      return com.kakao.actionbase.core.java.metadata.v3.common.MutationMode.DENY;
    }
    return mutationMode;
  }
}
