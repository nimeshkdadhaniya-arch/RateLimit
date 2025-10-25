package com.example.ratelimit.service;

import com.example.ratelimit.helper.DroppedRequest;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BoundedRetryQueueService {

    @Autowired
    private RateLimitService rateLimitService;

    private final ArrayBlockingQueue<DroppedRequest> queue;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RateLimitFilterFactoryConfig factoryConfig;
    private final long retryIntervalMs;

    public BoundedRetryQueueService(RateLimitFilterFactoryConfig factoryConfig,
                                    @Value("${ratelimit.retry.queue.size:10}") int capacity,
                                    @Value("${ratelimit.retry.interval.ms:90000}") long retryIntervalMs) {
        this.factoryConfig = factoryConfig;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.retryIntervalMs = retryIntervalMs;
    }

    public boolean enqueue(DroppedRequest req) {
        return queue.offer(req);
    }

    @PostConstruct
    public void startWorker() {
        scheduler.scheduleWithFixedDelay(this::processOnce, retryIntervalMs, retryIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void processOnce() {
        try {
            DroppedRequest req = queue.poll();
            log.debug("Retrying dropped request: acquired={}", req.getType());
            if (req == null) return;
            processRequest(req);
        } catch (Throwable t) {
            // swallow to keep scheduler alive; consider logging
        }
    }

    private void processRequest(DroppedRequest dr) {
        try {
            boolean acquired = rateLimitService.tryAcquire(dr.getRequest(), dr.getResponse());

            if (acquired) {
                // resume normal processing by dispatching/doFilter on the AsyncContext
                AsyncContext ctx = dr.getAsyncContext();
                FilterChain chain = dr.getFilterChain();
                ctx.dispatch();
            } else {
                sendTooManyRequestsAndComplete(dr.getResponse(),dr.getAsyncContext());
            }
        } catch (Exception e) {
            sendServerErrorAndComplete(dr.getResponse(), dr.getAsyncContext());
        }

    }

    private void sendTooManyRequestsAndComplete(HttpServletResponse response, AsyncContext ctx) {
        try {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String jsonResponse = "{"
                    + "\"error\": \"Rate limit exceeded\","
                    + "\"retry_after_ms\": 60000"
                    + "}";

            response.getWriter().write(jsonResponse);
            response.getWriter().flush();
        }  catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try { ctx.complete(); } catch (Exception ignored) {}
        }
    }

    private void sendServerErrorAndComplete(HttpServletResponse response, AsyncContext ctx) {
        try {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Internal Server Error");
            response.getWriter().flush();
        } catch (IOException ignored) {
        } finally {
            try {
                ctx.complete();
            } catch (Exception ignored) {
            }
        }
    }



}
