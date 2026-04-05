package com.loopers;

import com.loopers.application.coupon.CouponIssueRequestAppService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.queue.RedisQueueService;
import com.loopers.infrastructure.queue.RedisTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CommerceApiContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisTokenService redisTokenService;

    @Autowired
    private CouponIssueRequestAppService couponIssueRequestAppService;

    @Test
    void contextLoads() {
        StringRedisTemplate masterTemplate = applicationContext.getBean(RedisConfig.REDIS_TEMPLATE_MASTER, StringRedisTemplate.class);
        StringRedisTemplate queueTemplate = (StringRedisTemplate) ReflectionTestUtils.getField(redisQueueService, "redisTemplate");
        StringRedisTemplate tokenTemplate = (StringRedisTemplate) ReflectionTestUtils.getField(redisTokenService, "redisTemplateMaster");
        StringRedisTemplate couponTemplate = (StringRedisTemplate) ReflectionTestUtils.getField(couponIssueRequestAppService, "redisTemplate");

        assertThat(applicationContext.getBeansOfType(StringRedisTemplate.class)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(queueTemplate).isSameAs(masterTemplate);
        assertThat(tokenTemplate).isSameAs(masterTemplate);
        assertThat(couponTemplate).isSameAs(masterTemplate);
    }

    @TestConfiguration
    static class AdditionalRedisTemplateConfig {

        @Bean
        StringRedisTemplate secondaryStringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
            StringRedisTemplate redisTemplate = new StringRedisTemplate();
            redisTemplate.setConnectionFactory(lettuceConnectionFactory);
            return redisTemplate;
        }
    }
}
