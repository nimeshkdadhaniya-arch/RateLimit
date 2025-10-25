package com.example.ratelimit.impl;

import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.header.RateLimitHeaderInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class SlidingWindowLogRateLimit implements GlobalRateLimiter {

    private final int MAX_REQUESTS_PER_WINDOW;
    private final long WINDOW_SIZE_MS;
    private final Queue<Long> slidinglog ;


    public SlidingWindowLogRateLimit(int maxRequests, long windowInMillis) {
        this.MAX_REQUESTS_PER_WINDOW = maxRequests;
        this.WINDOW_SIZE_MS = windowInMillis;
        this.slidinglog  = new ConcurrentLinkedQueue<>();
    }


    @Override
    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();

        // Evict expired timestamps from the head of the queue.
       /* while (!slidinglog.isEmpty() &&  now - WINDOW_SIZE_MS > slidinglog.peek()) {
            slidinglog.poll();
        }*/
        while (true) {
            Long timestamp = slidinglog.peek();
            if (timestamp == null || now - timestamp < WINDOW_SIZE_MS) {   //8 sec -7 sec < 2sec
                break;
            }
            slidinglog.poll(); // Safe concurrent removal
        }

        log.debug("SlidingWindowLogRateLimit log size={}", slidinglog.size());
        // If the queue size is below the threshold, the request is allowed.
        if (slidinglog.size() < MAX_REQUESTS_PER_WINDOW) {
            slidinglog.offer(now);  // Record the timestamp of the allowed request.
            return true;
        }
        // If the queue is full, the request is rejected.
        return false;
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
        return slidinglog.size();
    }

    public long getUsedLimit() {
        return  MAX_REQUESTS_PER_WINDOW -slidinglog.size();
    }
    public long getNextWindowTime() {
        return slidinglog.peek() + WINDOW_SIZE_MS;
    }

}
