package com.loopers.application.like;

import com.loopers.application.like.LikeProductReadPort.LikeProductView;
import com.loopers.domain.model.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LikeQueryServiceTest {

    private LikeProductReadPort likeProductReadPort;
    private LikeQueryService service;

    @BeforeEach
    void setUp() {
        likeProductReadPort = mock(LikeProductReadPort.class);
        service = new LikeQueryService(likeProductReadPort);
    }

    @Nested
    @DisplayName("좋아요 목록 조회")
    class GetMyLikes {

        @Test
        @DisplayName("좋아요 목록 조회 성공")
        void getMyLikes_success() {
            // given
            UserId userId = UserId.of("test1234");
            LocalDateTime now = LocalDateTime.now();
            List<LikeProductView> likes = List.of(
                    new LikeProductView(1L, "상품1", 10000, null, 100, "나이키", now),
                    new LikeProductView(2L, "상품2", 20000, null, 50, "나이키", now.minusHours(1))
            );

            when(likeProductReadPort.findLikedProductsByUserId(userId)).thenReturn(likes);

            // when
            var result = service.getMyLikes(userId, "latest", null, null);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).brandName()).isEqualTo("나이키");
        }

        @Test
        @DisplayName("좋아요 목록이 비어있는 경우")
        void getMyLikes_empty() {
            // given
            UserId userId = UserId.of("test1234");
            when(likeProductReadPort.findLikedProductsByUserId(userId)).thenReturn(List.of());

            // when
            var result = service.getMyLikes(userId, "latest", null, null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("세일 상품만 필터링")
        void getMyLikes_filterBySaleYn() {
            // given
            UserId userId = UserId.of("test1234");
            LocalDateTime now = LocalDateTime.now();
            List<LikeProductView> likes = List.of(
                    new LikeProductView(1L, "일반상품", 10000, null, 100, "나이키", now),
                    new LikeProductView(2L, "세일상품", 100000, 70000, 50, "나이키", now)
            );

            when(likeProductReadPort.findLikedProductsByUserId(userId)).thenReturn(likes);

            // when
            var result = service.getMyLikes(userId, "latest", true, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).onSale()).isTrue();
        }

        @Test
        @DisplayName("가격순 정렬")
        void getMyLikes_sortByPrice() {
            // given
            UserId userId = UserId.of("test1234");
            LocalDateTime now = LocalDateTime.now();
            List<LikeProductView> likes = List.of(
                    new LikeProductView(1L, "비싼상품", 100000, null, 50, "나이키", now),
                    new LikeProductView(2L, "싼상품", 10000, null, 50, "나이키", now)
            );

            when(likeProductReadPort.findLikedProductsByUserId(userId)).thenReturn(likes);

            // when
            var result = service.getMyLikes(userId, "price_asc", null, null);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).price()).isEqualTo(10000);
            assertThat(result.get(1).price()).isEqualTo(100000);
        }
    }
}
