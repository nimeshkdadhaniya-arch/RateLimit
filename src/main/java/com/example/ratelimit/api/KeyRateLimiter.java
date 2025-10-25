package com.example.ratelimit.api;

public interface KeyRateLimiter extends RateLimiter {
    boolean tryAcquire(String key);
}
