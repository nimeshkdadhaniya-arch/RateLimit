package com.example.RateLimit.Filter;


import com.example.ratelimit.Filter.RateLimitFilter;
import com.example.ratelimit.api.IRateLimitService;
import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @Mock
    IRateLimitService rateLimitService;

    @Mock
    BoundedRetryQueueService retryQueueService;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain chain;

    @Mock
    AsyncContext asyncContext;

    private RateLimitFilter filterUnderTest;

    @BeforeEach
    void setup() throws Exception {
        filterUnderTest = new RateLimitFilter();
        // inject mocks into private fields
        setPrivateField(filterUnderTest, "rateLimitService", rateLimitService);
        setPrivateField(filterUnderTest, "retryQueueService", retryQueueService);
        // set async timeout value used in the filter
        setPrivateField(filterUnderTest, "ASYNC_TIMEOUT_MS", 30000L);
    }

    @Test
    void whenAllowed_then_continueChain() throws ServletException, IOException {
        when(rateLimitService.tryAcquire(request, response)).thenReturn(true);

        filterUnderTest.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_ACCEPTED);
        verify(response, never()).setStatus(429);
    }

    @Test
    void whenNotAllowed_andEnqueued_then_startAsync_andReturn202() throws ServletException, IOException {
        when(rateLimitService.tryAcquire(request, response)).thenReturn(false);
        when(request.isAsyncStarted()).thenReturn(false);
        when(request.startAsync()).thenReturn(asyncContext);
        // ensure enqueue returns true (accepted for background processing)
        when(retryQueueService.enqueue(any(DroppedRequest.class))).thenReturn(true);

        filterUnderTest.doFilterInternal(request, response, chain);

        verify(request, times(1)).startAsync();
        verify(asyncContext, times(1)).setTimeout(30000L);
        verify(retryQueueService, times(1)).enqueue(any(DroppedRequest.class));
        verify(response, times(1)).setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @Test
    void whenNotAllowed_andQueueFull_then_return429() throws ServletException, IOException {
        when(rateLimitService.tryAcquire(request, response)).thenReturn(false);
        when(request.isAsyncStarted()).thenReturn(false);
        when(request.startAsync()).thenReturn(asyncContext);
        // enqueue fails => queue full
        when(retryQueueService.enqueue(any(DroppedRequest.class))).thenReturn(false);

        filterUnderTest.doFilterInternal(request, response, chain);

        verify(retryQueueService, times(1)).enqueue(any(DroppedRequest.class));
        verify(response, times(1)).setStatus(429);
        // AsyncContext may still be created but should not be completed inside filter; ensure timeout set if started
        verify(asyncContext, times(1)).setTimeout(30000L);
    }

    // reflection helpers
    private void setPrivateField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        if (f.getType() == long.class && value instanceof Long) {
            f.setLong(target, (Long) value);
        } else {
            f.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}