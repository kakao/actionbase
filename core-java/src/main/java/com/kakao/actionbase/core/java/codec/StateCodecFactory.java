package com.kakao.actionbase.core.java.codec;

import com.kakao.actionbase.core.java.constant.Constant;

import java.util.concurrent.ConcurrentLinkedQueue;

public class StateCodecFactory {

  public StateCodecFactory() {}

  public StateCodec create() {
    return create(Constant.Codec.DEFAULT_POOL_SIZE, Constant.Codec.DEFAULT_BUFFER_SIZE);
  }

  public StateCodec create(int poolSize) {
    return create(poolSize, Constant.Codec.DEFAULT_BUFFER_SIZE);
  }

  public StateCodec create(int poolSize, int bufferSize) {
    ConcurrentLinkedQueue<StateCodecBuffer> bufferPool = new ConcurrentLinkedQueue<>();
    for (int i = 0; i < poolSize; i++) {
      bufferPool.add(new StateCodecBuffer(bufferSize));
    }
    return new StateCodec(bufferPool);
  }
}
