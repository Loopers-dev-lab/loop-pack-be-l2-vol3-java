package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingFacadeTest {

    RankingRepository rankingRepository = mock(RankingRepository.class);

    RankingFacade facade = new RankingFacade(rankingRepository);

    @DisplayName("applyView() 를 호출할 때, ")
    @Nested
    class ApplyView {

        @DisplayName("UTC 날짜 기준으로 0.1 점이 누적된다.")
        @Test
        void incrementsScoreByPoint1_withUtcDate() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            ProductViewEventPayload payload = new ProductViewEventPayload(42L, occurredAt);

            // act
            facade.applyView(payload);

            // assert
            verify(rankingRepository).incrementScore(42L, LocalDate.of(2026, 4, 8), 0.1);
        }

        @DisplayName("UTC 기준으로 날짜가 계산된다. (KST 기준 다음 날 시간이어도 UTC 날짜로 저장된다)")
        @Test
        void usesUtcDate_notKst() {
            // arrange
            // KST 2026-04-09 00:30 = UTC 2026-04-08 15:30
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 15, 30, 0, 0, ZoneOffset.UTC);
            ProductViewEventPayload payload = new ProductViewEventPayload(42L, occurredAt);

            // act
            facade.applyView(payload);

            // assert
            verify(rankingRepository).incrementScore(42L, LocalDate.of(2026, 4, 8), 0.1);
        }
    }

    @DisplayName("applyLike() 를 호출할 때, ")
    @Nested
    class ApplyLike {

        @DisplayName("delta = +1 이면 0.2 점이 누적된다.")
        @Test
        void incrementsScore_whenDeltaIsPositive() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1, occurredAt);

            // act
            facade.applyLike(payload);

            // assert
            verify(rankingRepository).incrementScore(42L, LocalDate.of(2026, 4, 8), 0.2);
        }

        @DisplayName("delta = -1 이면 -0.2 점이 차감된다.")
        @Test
        void decrementsScore_whenDeltaIsNegative() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            CatalogEventPayload payload = new CatalogEventPayload("uuid-2", "LIKE_DELETED", 1L, 42L, -1, occurredAt);

            // act
            facade.applyLike(payload);

            // assert
            verify(rankingRepository).incrementScore(42L, LocalDate.of(2026, 4, 8), -0.2);
        }
    }

    @DisplayName("applyOrder() 를 호출할 때, ")
    @Nested
    class ApplyOrder {

        @DisplayName("각 item 별로 0.7 * log(1 + quantity * unitPrice) 점이 누적된다.")
        @Test
        void incrementsScorePerItem_withLogScale() {
            // arrange
            List<OrderCreatedEventPayload.Item> items = List.of(
                    new OrderCreatedEventPayload.Item(42L, 2, 5000),
                    new OrderCreatedEventPayload.Item(99L, 1, 3000)
            );
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            OrderCreatedEventPayload payload = new OrderCreatedEventPayload("uuid-3", "ORDER_CREATED", 1L, "ORDER-001", 13000L, items, occurredAt);

            // act
            facade.applyOrder(payload);

            // assert
            double expectedIncrement42 = 0.7 * Math.log1p(2 * 5000);
            double expectedIncrement99 = 0.7 * Math.log1p(1 * 3000);
            verify(rankingRepository).incrementScore(42L, LocalDate.of(2026, 4, 8), expectedIncrement42);
            verify(rankingRepository).incrementScore(99L, LocalDate.of(2026, 4, 8), expectedIncrement99);
        }
    }
}
