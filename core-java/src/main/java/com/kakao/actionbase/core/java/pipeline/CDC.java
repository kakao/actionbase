package com.kakao.actionbase.core.java.pipeline;

import com.kakao.actionbase.core.java.pipeline.serializer.CDCDeserializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CDCDeserializer.class)
public interface CDC extends Message {}
