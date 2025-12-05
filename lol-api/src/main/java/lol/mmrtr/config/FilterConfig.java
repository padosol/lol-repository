package lol.mmrtr.config;

import lol.mmrtr.filter.InboundRateLimitFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    private final RedissonClient redissonClient;

    public FilterConfig(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean
    public FilterRegistrationBean<InboundRateLimitFilter> inboundRateLimitFilterRegistration() {
        FilterRegistrationBean<InboundRateLimitFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new InboundRateLimitFilter(redissonClient));

        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        registrationBean.setName("InboundRateLimitFilter");

        return registrationBean;
    }

}
