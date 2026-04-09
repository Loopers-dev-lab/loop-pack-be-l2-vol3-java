package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CatalogEventConsumerTest {

    @InjectMocks
    private CatalogEventConsumer consumer;

    @Mock
    private IdempotentProcessor idempotentProcessor;

    @Mock
    private MetricsService metricsService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private ConsumerRecord<String, byte[]> createRecord(String eventType, long productId) throws Exception {
        String json = String.format(
                "{\"eventId\":\"evt-1\",\"eventType\":\"%s\",\"payload\":\"{\\\"productId\\\":%d}\"}",
                eventType, productId);
        return new ConsumerRecord<>("product-interaction-events", 0, 0, "1", json.getBytes());
    }

    @Nested
    class 메시지_파싱 {

        @Test
        void 유효한_product_liked_메시지면_idempotentProcessor가_호출된다() throws Exception {
            doAnswer(inv -> { ((Runnable) inv.getArgument(4)).run(); return true; })
                    .when(idempotentProcessor).process(anyString(), eq("product.liked"), anyString(), anyString(), any());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(createRecord("product.liked", 1L)), ack);

            verify(idempotentProcessor).process(anyString(), eq("product.liked"), anyString(), anyString(), any());
            verify(ack).acknowledge();
        }

        @Test
        void 유효한_product_viewed_메시지면_idempotentProcessor가_호출된다() throws Exception {
            doAnswer(inv -> { ((Runnable) inv.getArgument(4)).run(); return true; })
                    .when(idempotentProcessor).process(anyString(), eq("product.viewed"), anyString(), anyString(), any());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(createRecord("product.viewed", 1L)), ack);

            verify(idempotentProcessor).process(anyString(), eq("product.viewed"), anyString(), anyString(), any());
        }
    }

    @Nested
    class 미지원_이벤트 {

        @Test
        void 알_수_없는_eventType이면_idempotentProcessor가_호출되지_않는다() throws Exception {
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(createRecord("unknown.event", 1L)), ack);

            verify(idempotentProcessor, never()).process(anyString(), anyString(), anyString(), anyString(), any());
            verify(ack).acknowledge();
        }
    }
}
