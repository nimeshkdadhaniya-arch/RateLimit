package com.example.RateLimit.integration;

import com.example.RateLimit.RateLimitAplicationTests;
import com.example.ratelimit.helper.RateLimiterTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.List;

public class RateLimitIntegrationTest extends RateLimitAplicationTests {
    @LocalServerPort
    private int port;

    private String baseUrl = "http://localhost:";
    private static RestTemplate restTemplate ;
    @BeforeAll
    public static void init() {
        restTemplate = new RestTemplate();
    }
    @BeforeEach
    public void setUp(){
        baseUrl = baseUrl + port + "/api/limit";
    }

    @Test
    public void testTokenBuckerLimiting() {
        List<RateLimiterTypeEnum> globalRateLimitTests = List.of(RateLimiterTypeEnum.TOKEN_BUCKET, RateLimiterTypeEnum.FIXED_WINDOW,RateLimiterTypeEnum.SLIDING_WINDOW_LOG);
        for (RateLimiterTypeEnum type : globalRateLimitTests) {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "?type=" + type.toString(), String.class);
            Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
            Assertions.assertEquals("Limit OK", response.getBody().toString());
            HttpHeaders httpHeaders = response.getHeaders();
            Assertions.assertNotNull(httpHeaders);
            Assertions.assertEquals(true,testHeaders(httpHeaders));
        }

    }

    @Test
    public void testSlidingLog() {
        List<RateLimiterTypeEnum> keyRateLimitTests = List.of(RateLimiterTypeEnum.SLIDING_WINDOW_LOG_USER);
        for (RateLimiterTypeEnum type : keyRateLimitTests) {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "?type=" + type.toString()+ "&key=User1", String.class);
            Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
            Assertions.assertEquals("Limit OK", response.getBody());
            HttpHeaders httpHeaders = response.getHeaders();
            Assertions.assertNotNull(httpHeaders);
            Assertions.assertEquals(true,testHeaders(httpHeaders));
        }

    }


    public boolean testHeaders(HttpHeaders headers){
        Assertions.assertTrue(headers.containsKey("X-RateLimit-Limit"));
        Assertions.assertTrue(headers.containsKey("X-RateLimit-Remaining"));
        Assertions.assertTrue(headers.containsKey("X-RateLimit-Reset"));
        return true;
    }

}
