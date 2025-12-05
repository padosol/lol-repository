package lol.mmrtr.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Duration;

public class InboundRateLimitFilter implements Filter {

    private final RRateLimiter rateLimiter;
    private static final String TEST_KEY = "inbound:api:limit";

    public InboundRateLimitFilter(RedissonClient redissonClient) {
        this.rateLimiter = redissonClient.getRateLimiter(TEST_KEY);

        this.rateLimiter.trySetRate(RateType.OVERALL, 100, Duration.ofSeconds(10));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (rateLimiter.tryAcquire(1)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getWriter().write("{\"error\": \"Rate Limit Exceeded\"}");
        }
    }
}
