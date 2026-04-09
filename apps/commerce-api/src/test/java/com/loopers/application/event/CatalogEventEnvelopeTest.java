package com.loopers.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.event.ProductViewedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CatalogEventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private ProductViewEventHandler viewListener;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(any(String.class), any(), any())).thenReturn(null);
        viewListener = new ProductViewEventHandler(kafkaTemplate, objectMapper);
    }

    @Nested
    class 조회_envelope {

        @Test
        void 상품_조회_이벤트는_eventType이_product_viewed인_envelope_Map으로_발행된다() throws Exception {
            viewListener.handleViewed(new ProductViewedEvent("u:1", 300L, Instant.now()));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq("300"), captor.capture());

            Object value = captor.getValue();
            assertThat(value).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) value;
            assertThat(envelope).containsKeys("eventId", "eventType", "payload");
            assertThat(envelope.get("eventType")).isEqualTo("product.viewed");
            assertThat(envelope.get("payload")).isInstanceOf(String.class);

            JsonNode innerNode = objectMapper.readTree((String) envelope.get("payload"));
            assertThat(innerNode.path("viewerId").asText()).isEqualTo("u:1");
            assertThat(innerNode.path("productId").asLong()).isEqualTo(300L);
        }
    }
}
