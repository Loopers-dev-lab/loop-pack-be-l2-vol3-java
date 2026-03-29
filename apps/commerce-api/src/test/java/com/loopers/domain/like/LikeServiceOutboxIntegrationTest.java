package com.loopers.domain.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.domain.product.Product;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeServiceOutboxIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class AddLike {

        @DisplayName("OutboxEvent가 PENDING 상태로 저장된다.")
        @Test
        void 좋아요_등록시_OutboxEvent가_PENDING_상태로_저장된다() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
            long userId = 1L;

            // act
            likeService.addLike(userId, product.getId());

            // assert
            var outboxEvents = outboxEventJpaRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outboxEvents.get(0).getEventType()).isEqualTo("LIKE_CREATED");
            assertThat(outboxEvents.get(0).getEventId()).isNotNull();
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class RemoveLike {

        @DisplayName("OutboxEvent가 PENDING 상태로 저장된다.")
        @Test
        void 좋아요_취소시_OutboxEvent가_PENDING_상태로_저장된다() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
            long userId = 1L;
            likeService.addLike(userId, product.getId());
            outboxEventJpaRepository.deleteAll();

            // act
            likeService.removeLike(userId, product.getId());

            // assert
            var outboxEvents = outboxEventJpaRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outboxEvents.get(0).getEventType()).isEqualTo("LIKE_DELETED");
            assertThat(outboxEvents.get(0).getEventId()).isNotNull();
        }
    }
}