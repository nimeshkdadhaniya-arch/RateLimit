package com.example.ratelimit.api;

import com.example.ratelimit.header.RateLimitHeaderInfo;
import jakarta.servlet.http.HttpServletRequest;

public interface RateLimiter {
    public RateLimitHeaderInfo getRateLimitHeaderInfo(HttpServletRequest request);
}
