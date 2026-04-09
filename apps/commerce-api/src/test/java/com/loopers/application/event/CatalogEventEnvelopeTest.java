package com.loopers.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.product.ProductService;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.infrastructure.product.ProductCacheManager;
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

/**
 * catalog-events 토픽 producer가 envelope { eventId, eventType, payload(string) } 형태로
 * Map 객체를 send 하는지 검증한다 (이중 인코딩 방지).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CatalogEventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private LikeCountEventHandler likeHandler;
    private ProductViewedEventListener viewListener;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(any(String.class), any(), any())).thenReturn(null);

        ProductService productService = mock(ProductService.class);
        ProductCacheManager cacheManager = mock(ProductCacheManager.class);
        likeHandler = new LikeCountEventHandler(productService, cacheManager, kafkaTemplate, objectMapper);
        viewListener = new ProductViewedEventListener(kafkaTemplate, objectMapper);
    }

    @Nested
    class 좋아요_envelope {

        @Test
        void 좋아요_이벤트는_eventType이_product_liked인_envelope_Map으로_발행된다() throws Exception {
            likeHandler.handleLiked(new ProductLikedEvent(1L, 100L));

            Map<String, Object> envelope = captureEnvelope("100");
            assertThat(envelope).containsKeys("eventId", "eventType", "payload");
            assertThat(envelope.get("eventType")).isEqualTo("product.liked");
            assertThat(envelope.get("eventId")).isInstanceOf(String.class);
            assertThat(envelope.get("payload")).isInstanceOf(String.class);

            JsonNode innerNode = objectMapper.readTree((String) envelope.get("payload"));
            assertThat(innerNode.path("productId").asLong()).isEqualTo(100L);
            assertThat(innerNode.path("userId").asLong()).isEqualTo(1L);
        }

        @Test
        void 좋아요_취소_이벤트는_eventType이_product_unliked인_envelope_Map으로_발행된다() {
            likeHandler.handleUnliked(new ProductUnlikedEvent(2L, 200L));

            Map<String, Object> envelope = captureEnvelope("200");
            assertThat(envelope.get("eventType")).isEqualTo("product.unliked");
        }
    }

    @Nested
    class 조회_envelope {

        @Test
        void 상품_조회_이벤트는_eventType이_product_viewed인_envelope_Map으로_발행된다() throws Exception {
            viewListener.on(new ProductViewedEvent("u:1", 300L, Instant.now()));

            Map<String, Object> envelope = captureEnvelope("300");
            assertThat(envelope.get("eventType")).isEqualTo("product.viewed");
            assertThat(envelope.get("payload")).isInstanceOf(String.class);

            JsonNode innerNode = objectMapper.readTree((String) envelope.get("payload"));
            assertThat(innerNode.path("viewerId").asText()).isEqualTo("u:1");
            assertThat(innerNode.path("productId").asLong()).isEqualTo(300L);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureEnvelope(String expectedKey) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq(expectedKey), captor.capture());
        Object value = captor.getValue();
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
