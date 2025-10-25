package com.example.ratelimit.helper;

public enum RateLimiterTypeEnum {
    TOKEN_BUCKET("TOKEN_BUCKET"),
    FIXED_WINDOW("FIXED_WINDOW"),
    SLIDING_WINDOW_LOG("SLIDING_WINDOW_LOG"),
    SLIDING_WINDOW_LOG_USER("SLIDING_WINDOW_LOG_USER");
    private final String type;

    RateLimiterTypeEnum(String type) {
        this.type = type;
    }
}
