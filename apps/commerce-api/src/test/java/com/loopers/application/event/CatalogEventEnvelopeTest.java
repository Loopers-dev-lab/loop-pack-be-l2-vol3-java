package com.loopers.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.product.ProductService;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.product.ProductCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Map 객체를 send하는지 검증한다. (이중 인코딩 방지)
 */
class CatalogEventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @SuppressWarnings("unchecked")
    private KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private LikeCountEventHandler likeHandler;
    private UserActivityEventHandler viewHandler;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(any(String.class), any(), any())).thenReturn(null);

        ProductService productService = mock(ProductService.class);
        ProductCacheManager cacheManager = mock(ProductCacheManager.class);
        likeHandler = new LikeCountEventHandler(productService, cacheManager, kafkaTemplate, objectMapper);

        viewHandler = new UserActivityEventHandler(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("LikeCountEventHandler.handleLiked는 envelope Map 객체를 send한다 (String 아님)")
    void liked_sendsEnvelopeMap() throws Exception {
        likeHandler.handleLiked(new ProductLikedEvent(1L, 100L));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq("100"), valueCaptor.capture());

        Object value = valueCaptor.getValue();
        assertThat(value).isInstanceOf(Map.class);
        Map<String, Object> envelope = (Map<String, Object>) value;
        assertThat(envelope).containsKeys("eventId", "eventType", "payload");
        assertThat(envelope.get("eventType")).isEqualTo("product.liked");
        assertThat(envelope.get("eventId")).isInstanceOf(String.class);
        assertThat(envelope.get("payload")).isInstanceOf(String.class);

        // payload 문자열이 inner JSON object여야 함 (객체로 readTree 가능)
        JsonNode innerNode = objectMapper.readTree((String) envelope.get("payload"));
        assertThat(innerNode.path("productId").asLong()).isEqualTo(100L);
        assertThat(innerNode.path("userId").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("LikeCountEventHandler.handleUnliked는 eventType=product.unliked envelope를 send한다")
    void unliked_sendsEnvelopeMap() {
        likeHandler.handleUnliked(new ProductUnlikedEvent(2L, 200L));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq("200"), valueCaptor.capture());

        Object value = valueCaptor.getValue();
        assertThat(value).isInstanceOf(Map.class);
        Map<String, Object> envelope = (Map<String, Object>) value;
        assertThat(envelope.get("eventType")).isEqualTo("product.unliked");
    }

    @Test
    @DisplayName("UserActivityEventHandler.handleProductViewed는 eventType=product.viewed envelope를 send한다")
    void viewed_sendsEnvelopeMap() throws Exception {
        viewHandler.handleProductViewed(new ProductViewedEvent("u:1", 300L, Instant.now()));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq("300"), valueCaptor.capture());

        Object value = valueCaptor.getValue();
        assertThat(value).isInstanceOf(Map.class);
        Map<String, Object> envelope = (Map<String, Object>) value;
        assertThat(envelope.get("eventType")).isEqualTo("product.viewed");
        assertThat(envelope.get("payload")).isInstanceOf(String.class);

        JsonNode innerNode = objectMapper.readTree((String) envelope.get("payload"));
        assertThat(innerNode.path("viewerId").asText()).isEqualTo("u:1");
        assertThat(innerNode.path("productId").asLong()).isEqualTo(300L);
    }
}
