package com.example.ratelimit.helper;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class TokenBucketConfig {
    private final long bucket_capacity;       // max tokens (burst size)
    private final long refillTokens ;   // tokens per second
    private final long refillIntervalMillis;
    public TokenBucketConfig(long bucket_capacity, long refillTokens,long refillIntervalMillis) {
        if (bucket_capacity <= 0 || refillTokens <= 0 || refillIntervalMillis < 0) {
            throw new IllegalArgumentException("Capacity and refillRate must be > 0");
        }
        this.bucket_capacity = bucket_capacity;
        this.refillTokens = refillTokens;
        this.refillIntervalMillis=refillIntervalMillis;
    }
}
