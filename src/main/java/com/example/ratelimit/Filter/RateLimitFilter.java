package com.example.ratelimit.Filter;

import com.example.ratelimit.api.IRateLimitService;
import com.example.ratelimit.helper.DroppedRequest;
import com.example.ratelimit.service.BoundedRetryQueueService;
import com.example.ratelimit.service.RateLimitService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private IRateLimitService rateLimitService;

    @Autowired
    private BoundedRetryQueueService retryQueueService;

    @Getter
    @Value("${async.timeout.ms}")
    private long ASYNC_TIMEOUT_MS;

    @Override
    public void doFilterInternal(HttpServletRequest  request, HttpServletResponse   response, FilterChain chain)
            throws IOException, ServletException {

        boolean isAquired =rateLimitService.tryAcquire(request, response);
        if (isAquired) {
            // Set rate limit headers
            chain.doFilter(request, response); // proceed with request

        } else {
           /* HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");

            String jsonResponse = "{"
                    + "\"error\": \"Rate limit exceeded\","
                    + "\"retry_after_ms\": 60000"
                    + "}";

            httpResponse.getWriter().write(jsonResponse);
            httpResponse.getWriter().flush();*/
            // attempt to enqueue for retry; if enqueued return false but mark accepted for later processing

            AsyncContext asyncContext = request.isAsyncStarted() ? request.getAsyncContext() : request.startAsync();
            asyncContext.setTimeout(ASYNC_TIMEOUT_MS);


            DroppedRequest dropped = new DroppedRequest(request, response,chain,asyncContext);
            boolean enqueued = retryQueueService.enqueue(dropped);
            if (enqueued) {
                log.debug("Enough token not found and Request enqueued for retry processing later.");
                response.setStatus(HttpServletResponse.SC_ACCEPTED); // accepted for background processing
            } else {
                log.debug("Enough token not found and Pending request queue is full, rejecting with 429.");
                response.setStatus(429); // queue full => reject
            }
        }

    }


}
