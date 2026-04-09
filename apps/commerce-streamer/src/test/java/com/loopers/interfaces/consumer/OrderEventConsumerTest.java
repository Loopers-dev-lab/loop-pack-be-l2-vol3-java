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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderEventConsumerTest {

    @InjectMocks
    private OrderEventConsumer consumer;

    @Mock
    private IdempotentProcessor idempotentProcessor;

    @Mock
    private MetricsService metricsService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private ConsumerRecord<String, byte[]> createRecord(String eventType, long orderId, String amount) throws Exception {
        String json = String.format(
                "{\"eventId\":\"evt-1\",\"eventType\":\"%s\",\"payload\":\"{\\\"orderId\\\":%d,\\\"amount\\\":\\\"%s\\\"}\"}",
                eventType, orderId, amount);
        return new ConsumerRecord<>("order-events", 0, 0, "1", json.getBytes());
    }

    @Nested
    class 메시지_파싱 {

        @Test
        void 주문_완료_메시지면_멱등_처리기가_호출된다() throws Exception {
            doAnswer(inv -> { ((Runnable) inv.getArgument(4)).run(); return true; })
                    .when(idempotentProcessor).process(anyString(), eq("order.completed"), anyString(), anyString(), any());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(createRecord("order.completed", 1L, "50000")), ack);

            verify(idempotentProcessor).process(anyString(), eq("order.completed"), anyString(), anyString(), any());
        }

        @Test
        void 결제_실패_메시지면_멱등_처리기가_호출되지_않는다() throws Exception {
            String json = "{\"eventId\":\"evt-1\",\"eventType\":\"payment.failed\",\"payload\":\"{}\"}";
            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("order-events", 0, 0, "1", json.getBytes());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(record), ack);

            verify(idempotentProcessor, never()).process(anyString(), anyString(), anyString(), anyString(), any());
            verify(ack).acknowledge();
        }
    }

    @Nested
    class 항목별_판매_집계 {

        private ConsumerRecord<String, byte[]> recordWithItems(String eventType) {
            String json = "{\"eventId\":\"evt-1\",\"eventType\":\"" + eventType + "\","
                    + "\"payload\":\"{\\\"orderId\\\":99,\\\"amount\\\":\\\"30000\\\","
                    + "\\\"items\\\":[{\\\"productId\\\":10,\\\"quantity\\\":2,\\\"unitPrice\\\":\\\"5000\\\"},"
                    + "{\\\"productId\\\":20,\\\"quantity\\\":1,\\\"unitPrice\\\":\\\"20000\\\"}]}\"}";
            return new ConsumerRecord<>("order-events", 0, 0, "99", json.getBytes());
        }

        @Test
        void 주문_완료면_각_항목별로_판매_수량과_금액이_증가한다() {
            doAnswer(inv -> { ((Runnable) inv.getArgument(4)).run(); return true; })
                    .when(idempotentProcessor).process(anyString(), eq("order.completed"), anyString(), anyString(), any());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(recordWithItems("order.completed")), ack);

            verify(metricsService).incrementSales(eq(10L), eq(2L), eq(new BigDecimal("10000")));
            verify(metricsService).incrementSales(eq(20L), eq(1L), eq(new BigDecimal("20000")));
            verifyNoMoreInteractions(metricsService);
        }

        @Test
        void 결제_취소면_각_항목별로_판매_수량과_금액이_차감된다() {
            doAnswer(inv -> { ((Runnable) inv.getArgument(4)).run(); return true; })
                    .when(idempotentProcessor).process(anyString(), eq("payment.canceled"), anyString(), anyString(), any());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(recordWithItems("payment.canceled")), ack);

            verify(metricsService).incrementSales(eq(10L), eq(-2L), eq(new BigDecimal("-10000")));
            verify(metricsService).incrementSales(eq(20L), eq(-1L), eq(new BigDecimal("-20000")));
            verifyNoMoreInteractions(metricsService);
        }
    }

    @Nested
    class 미지원_이벤트 {

        @Test
        void 알_수_없는_이벤트_타입이면_멱등_처리기가_호출되지_않는다() throws Exception {
            String json = "{\"eventId\":\"evt-1\",\"eventType\":\"unknown\",\"payload\":\"{}\"}";
            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("order-events", 0, 0, "1", json.getBytes());
            Acknowledgment ack = mock(Acknowledgment.class);

            consumer.consume(List.of(record), ack);

            verify(idempotentProcessor, never()).process(anyString(), anyString(), anyString(), anyString(), any());
        }
    }
}
