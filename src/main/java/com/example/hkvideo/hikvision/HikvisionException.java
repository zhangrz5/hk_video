package com.example.hkvideo.hikvision;

public class HikvisionException extends RuntimeException {

    private final String code;

    public HikvisionException(String message) {
        this(null, message);
    }

    public HikvisionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public HikvisionException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public String getCode() {
        return code;
    }
}
