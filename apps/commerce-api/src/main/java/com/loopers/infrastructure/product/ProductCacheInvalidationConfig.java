package com.loopers.infrastructure.product;

import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProductCacheInvalidationConfig {

    public static final String CHANNEL = "cache:product:invalidate";

    @Bean
    public ChannelTopic productCacheInvalidationTopic() {
        return new ChannelTopic(CHANNEL);
    }

    @Bean
    public MessageListenerAdapter productCacheMessageListener(ProductLocalCacheManager localCacheManager) {
        return new MessageListenerAdapter(new ProductCacheInvalidationSubscriber(localCacheManager));
    }

    @Bean
    public RedisMessageListenerContainer productCacheListenerContainer(
            @Qualifier(RedisConfig.CONNECTION_PUB_SUB) RedisConnectionFactory connectionFactory,
            MessageListenerAdapter productCacheMessageListener,
            ChannelTopic productCacheInvalidationTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(productCacheMessageListener, productCacheInvalidationTopic);
        return container;
    }

    @Slf4j
    @RequiredArgsConstructor
    static class ProductCacheInvalidationSubscriber {

        private final ProductLocalCacheManager localCacheManager;

        @SuppressWarnings("unused")
        public void handleMessage(String message) {
            log.debug("캐시 무효화 메시지 수신: {}", message);

            if (message.startsWith("detail:")) {
                String productIdStr = message.substring("detail:".length());
                localCacheManager.evictDetail(Long.parseLong(productIdStr));
            } else if ("list:all".equals(message)) {
                localCacheManager.evictAllLists();
            } else if ("all".equals(message)) {
                localCacheManager.evictAllDetails();
                localCacheManager.evictAllLists();
            }
        }
    }
}
