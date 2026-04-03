package com.loopers.infrastructure.outbox;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OutboxEventRecordListenerIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String BRAND_NAME = "나이키";
    private static final String PRODUCT_NAME = "에어맥스";
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand(BRAND_NAME));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(new Product(brandId, PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
    }

    @DisplayName("좋아요 등록 시 Outbox 기록 (BEFORE_COMMIT)")
    @Nested
    class LikeCreatedOutbox {

        @DisplayName("좋아요가 등록되면 같은 트랜잭션에서 LIKE_CREATED 이벤트가 Outbox에 기록된다")
        @Test
        void outboxRecordCreated_whenLikeCreated() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());

            // act
            likeFacade.create(USER_ID, product.getId());

            // assert
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(event.getAggregateId()).isEqualTo(product.getId());
            assertThat(event.getEventType()).isEqualTo("LIKE_CREATED");
            assertThat(event.getTopic()).isEqualTo("catalog-events");
            assertThat(event.getMessageKey()).isEqualTo(String.valueOf(product.getId()));
            assertThat(event.getPayload()).contains("productId");
            assertThat(event.getCreatedAt()).isNotNull();
        }

        @DisplayName("좋아요 등록이 실패하면 Outbox 기록도 함께 롤백된다 (TX 원자성)")
        @Test
        void outboxRecordRolledBack_whenLikeCreationFails() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            likeFacade.create(USER_ID, product.getId());
            long outboxCountBefore = outboxEventJpaRepository.count();

            // act — 중복 좋아요 → CONFLICT 예외 → TX 롤백
            try {
                likeFacade.create(USER_ID, product.getId());
            } catch (Exception ignored) {
            }

            // assert — Outbox에 추가 기록 없음 (TX 롤백으로 BEFORE_COMMIT도 취소)
            long outboxCountAfter = outboxEventJpaRepository.count();
            assertThat(outboxCountAfter).isEqualTo(outboxCountBefore);
        }
    }

    @DisplayName("좋아요 취소 시 Outbox 기록 (BEFORE_COMMIT)")
    @Nested
    class LikeCancelledOutbox {

        @DisplayName("좋아요가 취소되면 같은 트랜잭션에서 LIKE_CANCELLED 이벤트가 Outbox에 기록된다")
        @Test
        void outboxRecordCreated_whenLikeCancelled() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            likeFacade.create(USER_ID, product.getId());

            // act
            likeFacade.delete(USER_ID, product.getId());

            // assert
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            // LIKE_CREATED(등록 시) + LIKE_CANCELLED(취소 시) = 2건
            assertThat(events).hasSize(2);

            OutboxEvent cancelEvent = events.stream()
                    .filter(e -> e.getEventType().equals("LIKE_CANCELLED"))
                    .findFirst()
                    .orElseThrow();
            assertThat(cancelEvent.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(cancelEvent.getAggregateId()).isEqualTo(product.getId());
            assertThat(cancelEvent.getTopic()).isEqualTo("catalog-events");
        }
    }
}
