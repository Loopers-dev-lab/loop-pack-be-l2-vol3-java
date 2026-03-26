package com.loopers.application.outbox;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.productlike.ProductLikeV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Outbox 통합 테스트")
class OutboxIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("좋아요 → Outbox 저장 흐름")
    class OutboxFlow {

        @Test
        @DisplayName("성공: 좋아요 등록 시 PRODUCT_LIKED OutboxEvent가 DB에 INIT 상태로 저장된다")
        void registerLike_createsOutboxEventWithInitStatus() {
            // Given
            Brand brand = brandRepository.save(Brand.create("테스트브랜드", null, null));
            Product product = productRepository.save(
                    Product.create(brand.getId(), "테스트상품", null, new BigDecimal("10000"), 10, null)
            );
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<ProductLikeV1Dto.Response>> response = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            List<OutboxEvent> events = outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(OutboxStatus.INIT, 10);
            OutboxEvent outboxEvent = events.stream()
                    .filter(e -> e.getEventType().equals("PRODUCT_LIKED"))
                    .findFirst()
                    .orElseThrow();
            assertThat(outboxEvent.getAggregateId()).isEqualTo(String.valueOf(product.getId()));
            assertThat(outboxEvent.getEventId()).isNotNull();
            assertThat(outboxEvent.getPayload()).contains("\"productId\"");
            assertThat(outboxEvent.getOccurredAt()).isNotNull();
        }

        @Test
        @DisplayName("성공: 좋아요 취소 시 PRODUCT_UNLIKED OutboxEvent가 DB에 저장된다")
        void cancelLike_createsOutboxEventWithUnlikedType() {
            // Given
            Brand brand = brandRepository.save(Brand.create("테스트브랜드", null, null));
            Product product = productRepository.save(
                    Product.create(brand.getId(), "테스트상품", null, new BigDecimal("10000"), 10, null)
            );
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 좋아요 등록
            restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.Response>>() {}
            );

            // When - 좋아요 취소
            ResponseEntity<ApiResponse<Void>> cancelResponse = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            List<OutboxEvent> events = outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(OutboxStatus.INIT, 10);
            boolean hasUnlikedEvent = events.stream()
                    .anyMatch(e -> e.getEventType().equals("PRODUCT_UNLIKED"));
            assertThat(hasUnlikedEvent).isTrue();
        }

        @Test
        @DisplayName("성공: 좋아요 등록과 취소를 모두 수행하면 2건의 OutboxEvent가 저장된다")
        void registerAndCancel_createsTwoOutboxEvents() {
            // Given
            Brand brand = brandRepository.save(Brand.create("테스트브랜드", null, null));
            Product product = productRepository.save(
                    Product.create(brand.getId(), "테스트상품", null, new BigDecimal("10000"), 10, null)
            );
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When - 좋아요 등록
            restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.Response>>() {}
            );

            // When - 좋아요 취소
            restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // Then
            List<OutboxEvent> events = outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(OutboxStatus.INIT, 10);
            assertThat(events).hasSizeGreaterThanOrEqualTo(2);

            long likedCount = events.stream().filter(e -> e.getEventType().equals("PRODUCT_LIKED")).count();
            long unlikedCount = events.stream().filter(e -> e.getEventType().equals("PRODUCT_UNLIKED")).count();
            assertThat(likedCount).isGreaterThanOrEqualTo(1);
            assertThat(unlikedCount).isGreaterThanOrEqualTo(1);
        }
    }
}
