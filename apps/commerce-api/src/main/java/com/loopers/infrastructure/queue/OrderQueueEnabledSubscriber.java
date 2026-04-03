package com.loopers.infrastructure.queue;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class OrderQueueEnabledSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(OrderQueueEnabledSubscriber.class);

    private final RedisOrderQueueReader reader;
    private final RedisMessageListenerContainer listenerContainer;

    public OrderQueueEnabledSubscriber(
            RedisOrderQueueReader reader,
            RedisMessageListenerContainer listenerContainer
    ) {
        this.reader = reader;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic(OrderQueueConstants.ORDER_QUEUE_CHANNEL));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String value = new String(message.getBody(), StandardCharsets.UTF_8);
        if ("true".equals(value)) {
            reader.updateEnabled(true);
        } else if ("false".equals(value)) {
            reader.updateEnabled(false);
        } else {
            log.warn("대기열 채널에서 알 수 없는 메시지 수신: {}", value);
        }
    }
}
