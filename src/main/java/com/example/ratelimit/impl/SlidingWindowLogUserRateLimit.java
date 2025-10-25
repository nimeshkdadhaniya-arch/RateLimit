package com.example.ratelimit.impl;

import com.example.ratelimit.api.KeyRateLimiter;
import com.example.ratelimit.header.RateLimitHeaderInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@Slf4j
public class SlidingWindowLogUserRateLimit implements KeyRateLimiter {
    private final int MAX_REQUESTS_PER_WINDOW;
    private final long WINDOW_SIZE_MS;
    // Per-user request logs
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> userRequestLogs= new ConcurrentHashMap<>();
//Methods peekFirst(), pollFirst(), addLast() are all thread-safe and lock-free.
    private final Supplier<Long> timeSupplier;

    public SlidingWindowLogUserRateLimit(int maxRequestsPerWindow , long windowInMillis, Supplier<Long> timeSupplier) {
        if (maxRequestsPerWindow  <= 0 || windowInMillis <= 0) {
            throw new IllegalArgumentException("Limit and window size must be positive");
        }
        this.MAX_REQUESTS_PER_WINDOW = maxRequestsPerWindow ;
        this.WINDOW_SIZE_MS = windowInMillis;
        this.timeSupplier = timeSupplier;
    }


    @Override
    public boolean tryAcquire(String userId) {
        //long now = System.currentTimeMillis();
        long now = timeSupplier.get();
        // Get or create the user's log
        ConcurrentLinkedDeque<Long> logDequeue = userRequestLogs.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        log.debug("SlidingWindowLogUserRateLimit for user={} before cleanup log size={}",userId, logDequeue.size());
        // Remove old timestamps outside window
        cleanupOldRequests(logDequeue, now);
        log.debug("SlidingWindowLogUserRateLimit for user={} after cleanup log size={}",userId, logDequeue.size());
       /* // Evict expired timestamps from the head of the queue.
        while (!log.isEmpty() &&  now - WINDOW_SIZE_MS > log.peek()) {
            log.pollFirst();
        }*/
        // If the queue size is below the threshold, the request is allowed.
        if (logDequeue.size() < MAX_REQUESTS_PER_WINDOW) {   //practically thread safe for each user.
            logDequeue.addLast(now);  // Record the timestamp of the allowed request.
            return true;
        }
        // If the queue is full, the request is rejected.
        return false;
    }

    @Override
    public RateLimitHeaderInfo getRateLimitHeaderInfo(HttpServletRequest request)
    {
        String userId = request.getParameter("key");
        RateLimitHeaderInfo rateLimitHeaderInfo =new RateLimitHeaderInfo(getMaxLimit(),getRemainingLimit(userId),getUsedLimit(userId),getNextWindowTime(userId));
        return rateLimitHeaderInfo;
    }


    public long getMaxLimit() {
        return MAX_REQUESTS_PER_WINDOW;
    }
    public long getRemainingLimit(String userId) {

        int used = getUsedCount(userId);
        return Math.max(0, MAX_REQUESTS_PER_WINDOW - used);
    }

    public long getUsedLimit(String userId) {
        return getUsedCount(userId);
    }
    public long getNextWindowTime(String userId) {

        ConcurrentLinkedDeque<Long> userIdlog = userRequestLogs.get(userId);
        if (userIdlog == null || userIdlog.isEmpty()) {
            return System.currentTimeMillis();
        }
        Long oldest = userIdlog.peekFirst();
        if (oldest == null) {
            return System.currentTimeMillis();
        }
        return oldest + WINDOW_SIZE_MS; // epoch ms when the oldest request leaves the window
    }

    // helper
    private int getUsedCount(String userId) {
        ConcurrentLinkedDeque<Long> userIdlog = userRequestLogs.get(userId);
        return (userIdlog == null) ? 0 : userIdlog.size();
    }

    // Removes old timestamps efficiently
    private void cleanupOldRequests(ConcurrentLinkedDeque<Long> logDequeueParam, long now) {
        Long first;
        while ((first = logDequeueParam.peekFirst()) != null && now - first >= WINDOW_SIZE_MS) {
            logDequeueParam.pollFirst(); // removes oldest request timestamp
        }
    }
}
