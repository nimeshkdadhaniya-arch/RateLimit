package com.example.RateLimit.integration;

import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;




@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitSlidingUserLogTest1 {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoundedRetryQueueService boundedRetryQueueService;

   // @MockitoBean
   // private BoundedRetryQueueService retryQueueService = mock(BoundedRetryQueueService.class);

    @TestConfiguration
    static class TestConfig {
        @Bean
        public BoundedRetryQueueService boundedRetryQueueService() {
            // You can either:
            // 1️⃣ return a real implementation
            // 2️⃣ or a simple stub for controlled behavior
            return new BoundedRetryQueueService(5, 500000L); // Example with small queue size and short retry interval
        }
    }

    @Test
    void  testRateLimitAndQueueBehavior() throws Exception {
        String baseUrl = "/api/limit?type=SLIDING_WINDOW_LOG_USER&key=userid1";

        //Thread.sleep(31000); //queue poll first time.

        Thread.sleep(20000);//25 sec
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());


        Thread.sleep(2000); //1 sec simulate delay
        // Request 2 at 12:54:15 -> success
        mockMvc.perform(get(baseUrl))
                .andExpect(status().isOk());

        Thread.sleep(2000);//1 sec
        // Request 3 at 12:54:25 -> blocked (added to queue)




System.out.println("Submitting 3rd request which should be queued...");

        MvcResult result = mockMvc.perform(get(baseUrl))
                .andExpect(status().isAccepted())
                .andReturn();
        //Thread.sleep(1000);
        //Thread.sleep(8000);
        DroppedRequest dropped = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped);

        assertNotNull(dropped);
        assertNotNull(dropped.getResponse());
       //defect code: assertEquals(dropped.getResponse().getStatus(),429);
       // Assertions.assertEquals("Rate limit exceeded", dropped.getResponse().getWriter().toString());

        assertEquals(dropped.getResponse().getStatus(),202); // defect fixed
        System.out.println("3rd queue request retrying...");
        //retry queue message processing
        Thread.sleep(65000);
        DroppedRequest dropped1 = boundedRetryQueueService.getQueue().poll();
        boundedRetryQueueService.processRequest(dropped1);

        assertNotNull(dropped1);
        assertEquals(dropped1.getResponse().getStatus(),200); // defect fixed
    }
}
