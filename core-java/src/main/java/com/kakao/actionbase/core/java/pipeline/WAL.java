package com.kakao.actionbase.core.java.pipeline;

import com.kakao.actionbase.core.java.pipeline.serializer.WALDeserializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = WALDeserializer.class)
public interface WAL extends Message {}
