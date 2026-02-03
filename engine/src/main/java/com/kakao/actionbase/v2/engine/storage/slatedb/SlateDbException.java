package com.kakao.actionbase.v2.engine.storage.slatedb;

public class SlateDbException extends RuntimeException {
    private final int code;

    public SlateDbException(int code, String message) {
        super("SlateDB error (" + code + "): " + message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
