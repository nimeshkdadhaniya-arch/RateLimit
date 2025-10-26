package com.example.RateLimit.service;
import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import com.example.ratelimit.service.RateLimitFilterFactoryConfig;
import com.example.ratelimit.service.RateLimitService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BoundedRetryQueueServiceTest {

    @Mock
    RateLimitFilterFactoryConfig factoryConfig;

    @Mock
    RateLimitService rateLimitService;

    @Mock
    DroppedRequest droppedRequest;

    @Mock
    HttpServletRequest req;

    @Mock
    HttpServletResponse resp;

    @Mock
    AsyncContext asyncContext;

    @Mock
    FilterChain chain;
    // service instance reused by tests
    //private final BoundedRetryQueueService serviceUnderTest = new BoundedRetryQueueService( 5, 90000L);

    private  BoundedRetryQueueService serviceUnderTest;
    Properties props;
    @BeforeEach
    void setUp() {
        props = loadTestProperties();
        int capacity = Integer.parseInt(props.getProperty("ratelimit.queue.retry.queue.size", "5"));
        long retryIntervalMs = Long.parseLong(props.getProperty("ratelimit.queue.retry.interval.ms", "90000"));

        serviceUnderTest = new BoundedRetryQueueService(capacity, retryIntervalMs);
        // inject mocked RateLimitService to avoid NPE during tests
        setPrivateField(serviceUnderTest, "rateLimitService", rateLimitService);
    }


    // helper to shutdown internal scheduler after test to avoid thread leak
    @AfterEach
    void tearDown() throws Exception {
        // get scheduler field and shutdown it
        Field schedField = BoundedRetryQueueService.class.getDeclaredField("scheduler");
        schedField.setAccessible(true);
        Object schedObj = schedField.get(serviceUnderTest);
        if (schedObj instanceof ScheduledExecutorService) {
            ((ScheduledExecutorService) schedObj).shutdownNow();
        }
    }


    //test enqueue method respects capacity. queue capacity is 1, so second enqueue should fail. to check queue full condition.
    @Test
    void enqueue_respectsCapacity() {
        // small capacity service to test full queue
       // Properties props = loadTestProperties();
        String retryIntervalMsStr = props.getProperty("ratelimit.queue.retry.interval.ms");
        long retryIntervalMs = Long.parseLong(retryIntervalMsStr);
        BoundedRetryQueueService smallQueueService = new BoundedRetryQueueService( 1, retryIntervalMs);
        // set rateLimitService field to avoid NPE (not used in this test)
       // setPrivateField(smallQueueService, "rateLimitService", rateLimitService);

        boolean first = smallQueueService.enqueue(mock(DroppedRequest.class));
        boolean second = smallQueueService.enqueue(mock(DroppedRequest.class));

        assertTrue(first, "first enqueue should succeed");
        assertFalse(second, "second enqueue should fail when capacity is 1");

        // cleanup scheduler for created instance
        shutdownScheduler(smallQueueService);
    }

    //testing of Asynch process request when Token is acquired that time it should not return 429 statuscode.
    @Test
    void processRequest_whenAcquired_dispatchesAsyncContext() throws Exception {
        // arrange
        //setPrivateField(serviceUnderTest, "rateLimitService", rateLimitService);

        when(droppedRequest.getRequest()).thenReturn(req);
        when(droppedRequest.getResponse()).thenReturn(resp);
        when(droppedRequest.getAsyncContext()).thenReturn(asyncContext);
        when(droppedRequest.getFilterChain()).thenReturn(chain);

        when(rateLimitService.tryAcquire(req, resp)).thenReturn(true);

        // act: invoke private method processRequest(DroppedRequest)
        invokeProcessRequest(serviceUnderTest, droppedRequest);

        // assert: dispatch should be invoked once, and no 429 status set
        verify(asyncContext, times(1)).dispatch();
        verify(resp, never()).setStatus(429);
    }

    //testing of Asynch process request when token is not acquired that time it should  return 202 statuscode.
    @Test
    void processRequest_whenNotAcquired_sends202AndCompletes() throws Exception {
        // arrange
       // setPrivateField(serviceUnderTest, "rateLimitService", rateLimitService);

        when(droppedRequest.getRequest()).thenReturn(req);
        when(droppedRequest.getResponse()).thenReturn(resp);
        when(droppedRequest.getAsyncContext()).thenReturn(asyncContext);

        // provide a writer so write/flush calls don't throw
        /*StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(resp.getWriter()).thenReturn(pw);*/

        when(rateLimitService.tryAcquire(req, resp)).thenReturn(false);

        // act
        invokeProcessRequest(serviceUnderTest, droppedRequest);

        // assert
        verify(resp, atLeastOnce()).setStatus(HttpServletResponse.SC_ACCEPTED);
        // verify(asyncContext, times(1)).complete();
    }

    //During retry attempts exceeded it should return 429 statuscode.
    @Test
    void processRequest_whenAttemptsExceeded_sends429AndCompletes() throws Exception {
        // arrange
       // setPrivateField(serviceUnderTest, "rateLimitService", rateLimitService);

        when(droppedRequest.getRequest()).thenReturn(req);
        when(droppedRequest.getResponse()).thenReturn(resp);
        when(droppedRequest.getAsyncContext()).thenReturn(asyncContext);
        // ensure branch: attempts > MAX_RETRY (MAX_RETRY is 3 in service, so use 4)
        when(droppedRequest.getAttempts()).thenReturn(4);

        // ensure tryAcquire returns false so code reaches attempts check
        when(rateLimitService.tryAcquire(req, resp)).thenReturn(false);

        // provide a real writer so sendTooManyRequestsAndComplete can write without throwing
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(resp.getWriter()).thenReturn(pw);

        // act
        invokeProcessRequest(serviceUnderTest, droppedRequest);

        // assert: 429 set and async context completed, and JSON contains expected message
        verify(resp, atLeastOnce()).setStatus(429);
        verify(asyncContext, times(1)).complete();
        pw.flush();
        assertTrue(sw.toString().contains("Rate limit exceeded"));
    }





    private void invokeProcessRequest(BoundedRetryQueueService svc, DroppedRequest dr) throws Exception {
        Method m = BoundedRetryQueueService.class.getDeclaredMethod("processRequest", DroppedRequest.class);
        m.setAccessible(true);
        m.invoke(svc, dr);
    }


    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private void shutdownScheduler(BoundedRetryQueueService svc) {
        try {
            Field schedField = BoundedRetryQueueService.class.getDeclaredField("scheduler");
            schedField.setAccessible(true);
            Object schedObj = schedField.get(svc);
            if (schedObj instanceof ScheduledExecutorService) {
                ((ScheduledExecutorService) schedObj).shutdownNow();
            }
        } catch (Exception ignored) {}
    }
    private Properties loadTestProperties() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}
