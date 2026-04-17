package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeResult;
import com.loopers.domain.like.LikeToggleService;
import com.loopers.domain.like.event.LikeToggledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LikeTransactionServiceTest {

    @InjectMocks
    private LikeTransactionService likeTransactionService;

    @Mock
    private LikeService likeService;

    @Mock
    private LikeToggleService likeToggleService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @DisplayName("좋아요 등록")
    @Nested
    class DoLike {

        @DisplayName("새 좋아요가 생성되면 LikeToggledEvent(liked=true)를 발행한다")
        @Test
        void publishesLikeToggledEventWhenNewLikeCreated() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel newLike = new LikeModel(userId, productId);
            LikeResult result = new LikeResult(Optional.of(newLike), true);

            given(likeService.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
            given(likeToggleService.like(Optional.empty(), userId, productId)).willReturn(result);

            // when
            likeTransactionService.doLike(userId, productId);

            // then
            ArgumentCaptor<LikeToggledEvent> captor = ArgumentCaptor.forClass(LikeToggledEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().productId()).isEqualTo(100L);
            assertThat(captor.getValue().liked()).isTrue();
        }

        @DisplayName("이미 좋아요 상태면 이벤트를 발행하지 않는다")
        @Test
        void doesNotPublishEventWhenAlreadyLiked() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel existing = new LikeModel(userId, productId);
            LikeResult result = new LikeResult(Optional.empty(), false);

            given(likeService.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existing));
            given(likeToggleService.like(Optional.of(existing), userId, productId)).willReturn(result);

            // when
            likeTransactionService.doLike(userId, productId);

            // then
            then(eventPublisher).should(never()).publishEvent(any(LikeToggledEvent.class));
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class DoUnlike {

        @DisplayName("활성 좋아요가 있으면 LikeToggledEvent(liked=false)를 발행한다")
        @Test
        void publishesLikeToggledEventWhenUnliked() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel activeLike = new LikeModel(userId, productId);

            given(likeService.findActiveLike(userId, productId)).willReturn(Optional.of(activeLike));

            // when
            likeTransactionService.doUnlike(userId, productId);

            // then
            ArgumentCaptor<LikeToggledEvent> captor = ArgumentCaptor.forClass(LikeToggledEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().productId()).isEqualTo(100L);
            assertThat(captor.getValue().liked()).isFalse();
        }

        @DisplayName("활성 좋아요가 없으면 이벤트를 발행하지 않는다")
        @Test
        void doesNotPublishEventWhenNoActiveLike() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            given(likeService.findActiveLike(userId, productId)).willReturn(Optional.empty());

            // when
            likeTransactionService.doUnlike(userId, productId);

            // then
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }
}
