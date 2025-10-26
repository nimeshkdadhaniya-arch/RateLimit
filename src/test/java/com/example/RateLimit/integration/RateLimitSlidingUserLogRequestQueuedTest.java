package com.example.RateLimit.integration;

import com.example.RateLimit.Helper.SlidingLogUserConfig;
import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@EnableConfigurationProperties(SlidingLogUserConfig.class)
public class RateLimitSlidingUserLogRequestQueuedTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoundedRetryQueueService boundedRetryQueueService;



    @Autowired
    private SlidingLogUserConfig slidingLogUserConfig;


   /* @TestConfiguration
    static class TestConfig { //beans that should only exist for tests, not for the main application
        @Bean
        public BoundedRetryQueueService boundedRetryQueueService() {
            return new BoundedRetryQueueService(5, Integer.MAX_VALUE); //stop to schedule internal scheduler to test asynch testing.
        }
    }*/

    // moved bean into TestConfiguration to avoid "Test classes cannot include @Bean methods"
    @TestConfiguration
    static class TestConfig {
        @Bean
        public BoundedRetryQueueService boundedRetryQueueService(SlidingLogUserConfig slidingLogUserConfig) {
            int queueSize = slidingLogUserConfig.getLimit();
            return new BoundedRetryQueueService(5, Integer.MAX_VALUE); //stop to schedule internal scheduler to test asynch testing.
        }
    }


    //integration test to check pending MQ request retry for sliding log user rate limit
    @Test
    void  testSlidingLogUserPendingRequestRetry() throws Exception {
        String baseUrl = "/api/limit?type=SLIDING_WINDOW_LOG_USER&key=userid1";
        Thread.sleep(slidingLogUserConfig.getTimeWindowMillis() ); // wait for any previous processing to complete
        if(boundedRetryQueueService.getQueue().size()>0){
            log.info("Clearing up existing items in queue before test execution");
            // cleanup: drain queue (optional)
            while (boundedRetryQueueService.getQueue().poll() != null) {
                // do nothing, just draining
            }
        }

        // Request 1 at 12:54:05 -> success
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());


        // Request 2 at 12:54:15 -> success
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());


        // Request 3 at 12:54:25 -> blocked (added to queue)
        log.info("Submitting 3rd request which should be queued...");

        MvcResult result = mockMvc.perform(get(baseUrl))
                .andExpect(status().isAccepted())
                .andReturn();
        //call poll since first two requests are successful and 3rd is queued within window of 1 minute configured in rules.
        DroppedRequest dropped = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped);

        assertNotNull(dropped);
        assertNotNull(dropped.getResponse());
       //defect code: assertEquals(dropped.getResponse().getStatus(),429);
        // Sourav sen asked me to reproduce this error which was reproduced sucessfully.
        //How to fix: solution is to retry queue MAX_RETRY times before giving up with fix delay or exponential backoff approach.


        assertEquals(dropped.getResponse().getStatus(),202); // defect fixed ::: request accepted for processing
        log.info("3rd request queued  retrying...");
        //retry queue message processing
        Thread.sleep((long) (slidingLogUserConfig.getTimeWindowMillis()* 1.2)); // wait for window to expire
        DroppedRequest dropped1 = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped1);

        assertNotNull(dropped1);
        assertEquals(dropped1.getResponse().getStatus(),200); // retry should succeed now..
    }




    //integration test to check max limit of pending requests in queue
    @Test
    void  testSlidingLogUserPendingRequestMaxLimit() throws Exception {
        String baseUrl = "/api/limit?type=SLIDING_WINDOW_LOG_USER&key=userid1";

        // First two should succeed
        mockMvc.perform(get(baseUrl)).andExpect(status().isOk());
        mockMvc.perform(get(baseUrl)).andExpect(status().isOk());

        // Next 5 should be queued (202)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get(baseUrl)).andExpect(status().isAccepted());
        }

        // Queue should be full (capacity = 5)
        assertEquals(5, boundedRetryQueueService.getQueue().size(), "queue should contain 5 items");

        // 6th request should be rejected as queue capacity is full
        mockMvc.perform(get(baseUrl)).andExpect(status().isTooManyRequests());

        // cleanup: drain queue (optional)
        while (boundedRetryQueueService.getQueue().poll() != null) {
            // do nothing, just draining
        }
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
