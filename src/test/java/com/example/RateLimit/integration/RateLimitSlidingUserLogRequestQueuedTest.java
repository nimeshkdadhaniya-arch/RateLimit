package com.example.RateLimit.integration;

import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitSlidingUserLogRequestQueuedTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoundedRetryQueueService boundedRetryQueueService;


    @Value("${sliding.log.user.timewindowMillis}")
    private int windowlogUserTimeoutMillis;



    @Value("${ratelimit.retry.queue.size}")
    private static int blockingQueueCapacity;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public BoundedRetryQueueService boundedRetryQueueService() {
            return new BoundedRetryQueueService(5, Integer.MAX_VALUE); //stop to schedule internal scheduler to test asynch testing.
        }
    }

    @Test
    void  testRateLimitAndQueueBehavior() throws Exception {
        String baseUrl = "/api/limit?type=SLIDING_WINDOW_LOG_USER&key=userid1";

        // Request 1 at 12:54:05 -> success
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());


        // Request 2 at 12:54:15 -> success
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());


        // Request 3 at 12:54:25 -> blocked (added to queue)
        log.debug("Submitting 3rd request which should be queued...");

        MvcResult result = mockMvc.perform(get(baseUrl))
                .andExpect(status().isAccepted())
                .andReturn();
        //call poll since first two requests are successful and 3rd is queued within window of 1 minute configured in rules.
        DroppedRequest dropped = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped);

        assertNotNull(dropped);
        assertNotNull(dropped.getResponse());
       //defect code: assertEquals(dropped.getResponse().getStatus(),429);
        // Sourav sen asked me to reproduce this error which was reported without retry of queued message.


        assertEquals(dropped.getResponse().getStatus(),202); // defect fixed request accepted for processing
        log.debug("3rd request queued  retrying...");
        //retry queue message processing
        Thread.sleep((long) (windowlogUserTimeoutMillis * 1.2)); // wait for window to expire
        DroppedRequest dropped1 = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped1);

        assertNotNull(dropped1);
        assertEquals(dropped1.getResponse().getStatus(),200); // defect fixed
    }
}
