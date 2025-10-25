package com.example.ratelimit.api;

public interface GlobalRateLimiter extends RateLimiter {
    public boolean tryAcquire();
}
