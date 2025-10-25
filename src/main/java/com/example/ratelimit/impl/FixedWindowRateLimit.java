package com.example.ratelimit.impl;

import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.header.RateLimitHeaderInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class FixedWindowRateLimit implements GlobalRateLimiter {
    // Rate limit configuration
    private  int MAX_REQUESTS_PER_WINDOW; // max requests per window, 2 request per window
    private  long WINDOW_SIZE_MS ; // 1 minute window
    // Counter for the number of requests in the current window.
    private final AtomicInteger counter = new AtomicInteger();

    // The start time of the current window.
    private AtomicLong windowStartTime;

    public FixedWindowRateLimit(int max_requests_per_window, long window_size_ms) {
        this.MAX_REQUESTS_PER_WINDOW = max_requests_per_window;
        this.WINDOW_SIZE_MS = window_size_ms;
        this.windowStartTime =  new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public boolean tryAcquire() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();


        /*if (currentTime - windowStart >= WINDOW_SIZE_MS) {
            counter.set(0);
            windowStartTime = currentTime;
        }*/
        log.debug("FixedWindowRateLimit counter={}", counter.get());
        // If the current window has expired, reset atomically (only one thread succeeds)
        if (currentTime - windowStart >= WINDOW_SIZE_MS) {
            if (windowStartTime.compareAndSet(windowStart, currentTime)) {
                // Only the thread that wins the race resets the counter
                counter.set(0);
            }
        }

           return counter.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;

    }

    @Override
    public RateLimitHeaderInfo getRateLimitHeaderInfo(HttpServletRequest request)
    {
        RateLimitHeaderInfo rateLimitHeaderInfo =new RateLimitHeaderInfo(getMaxLimit(),getRemainingLimit(),getUsedLimit(),getNextWindowTime());
        return rateLimitHeaderInfo;
    }



    public long getMaxLimit() {
        return MAX_REQUESTS_PER_WINDOW;
    }
    public long getRemainingLimit() {
        return MAX_REQUESTS_PER_WINDOW - counter.get();
    }

    public long getUsedLimit() {
        return  counter.get();
    }
    public long getNextWindowTime() {
        return windowStartTime.get() + WINDOW_SIZE_MS;
    }
}
