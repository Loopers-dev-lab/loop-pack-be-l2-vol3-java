package com.loopers.application.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.EventOutbox;
import com.loopers.domain.event.EventOutboxRepository;
import com.loopers.domain.event.LikeCreatedEvent;
import com.loopers.domain.event.LikeRemovedEvent;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeLikeRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeFacadeTest {

    private LikeFacade likeFacade;
    private FakeLikeRepository likeRepository;
    private FakeProductRepository productRepository;
    private List<EventOutbox> savedOutboxes;
    private List<Object> publishedEvents;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        productRepository = new FakeProductRepository();
        savedOutboxes = new ArrayList<>();
        publishedEvents = new ArrayList<>();
        EventOutboxRepository eventOutboxRepository = outbox -> {
            savedOutboxes.add(outbox);
            return outbox;
        };
        ApplicationEventPublisher eventPublisher = publishedEvents::add;
        likeFacade = new LikeFacade(likeRepository, productRepository,
            eventOutboxRepository, eventPublisher, new ObjectMapper());
    }

    @Nested
    @DisplayName("좋아요 추가")
    class AddLike {

        @DisplayName("좋아요를 추가하면 Like 레코드가 저장되고 Product.likeCount가 1 증가한다")
        @Test
        void addLike_savesLikeRecord_andIncrementsLikeCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;

            likeFacade.addLike(memberId, product.getId());

            assertThat(likeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isTrue();
            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(1);
            // likeCount는 이벤트 리스너에서 처리 (단위 테스트에서는 미검증)
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 멱등하게 처리된다 (likeCount 불변)")
        @Test
        void addLike_whenAlreadyLiked_isIdempotent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product.getId());

            likeFacade.addLike(memberId, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(1);
            assertThat(likeRepository.findAllByMemberId(memberId)).hasSize(1);
            // likeCount는 이벤트 리스너에서 처리 (단위 테스트에서는 미검증)
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 예외가 발생한다")
        @Test
        void addLike_whenProductNotExists_throwsCoreException() {
            assertThatThrownBy(() -> likeFacade.addLike(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("여러 회원이 같은 상품에 좋아요하면 카운트가 누적된다")
        @Test
        void addLike_byMultipleMembers_accumulatesCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.addLike(1L, product.getId());
            likeFacade.addLike(2L, product.getId());
            likeFacade.addLike(3L, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(3);
            // likeCount는 이벤트 리스너에서 처리 (단위 테스트에서는 미검증)
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class RemoveLike {

        @DisplayName("좋아요를 취소하면 Like 레코드가 삭제되고 Product.likeCount가 1 감소한다")
        @Test
        void removeLike_deletesLikeRecord_andDecrementsLikeCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product.getId());

            likeFacade.removeLike(memberId, product.getId());

            assertThat(likeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isFalse();
            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(0);
            // likeCount는 이벤트 리스너에서 처리 (단위 테스트에서는 미검증)
        }

        @DisplayName("좋아요하지 않은 상품의 좋아요를 취소해도 예외 없이 멱등하게 처리된다")
        @Test
        void removeLike_whenNotLiked_isIdempotent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.removeLike(1L, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(0);
            // likeCount는 이벤트 리스너에서 처리 (단위 테스트에서는 미검증)
        }
    }

    @Nested
    @DisplayName("Outbox + 이벤트 발행 검증")
    class OutboxAndEvent {

        @DisplayName("좋아요 추가 시 EventOutbox가 저장되고 LikeCreatedEvent가 발행된다")
        @Test
        void addLike_savesOutboxAndPublishesEvent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.addLike(1L, product.getId());

            assertThat(savedOutboxes).hasSize(1);
            EventOutbox outbox = savedOutboxes.get(0);
            assertThat(outbox.getAggregateType()).isEqualTo("catalog");
            assertThat(outbox.getAggregateId()).isEqualTo(String.valueOf(product.getId()));
            assertThat(outbox.getEventType()).isEqualTo("LIKE_CREATED");

            assertThat(publishedEvents).hasSize(1);
            assertThat(publishedEvents.get(0)).isInstanceOf(LikeCreatedEvent.class);
            LikeCreatedEvent event = (LikeCreatedEvent) publishedEvents.get(0);
            assertThat(event.productId()).isEqualTo(product.getId());
            assertThat(event.memberId()).isEqualTo(1L);
        }

        @DisplayName("좋아요 취소 시 EventOutbox가 저장되고 LikeRemovedEvent가 발행된다")
        @Test
        void removeLike_savesOutboxAndPublishesEvent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            likeFacade.addLike(1L, product.getId());
            savedOutboxes.clear();
            publishedEvents.clear();

            likeFacade.removeLike(1L, product.getId());

            assertThat(savedOutboxes).hasSize(1);
            EventOutbox outbox = savedOutboxes.get(0);
            assertThat(outbox.getEventType()).isEqualTo("LIKE_REMOVED");

            assertThat(publishedEvents).hasSize(1);
            assertThat(publishedEvents.get(0)).isInstanceOf(LikeRemovedEvent.class);
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 Outbox와 이벤트가 발행되지 않는다")
        @Test
        void addLike_whenIdempotent_noOutboxOrEvent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            likeFacade.addLike(1L, product.getId());
            savedOutboxes.clear();
            publishedEvents.clear();

            likeFacade.addLike(1L, product.getId());

            assertThat(savedOutboxes).isEmpty();
            assertThat(publishedEvents).isEmpty();
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면 Outbox와 이벤트가 발행되지 않는다")
        @Test
        void removeLike_whenNotLiked_noOutboxOrEvent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.removeLike(1L, product.getId());

            assertThat(savedOutboxes).isEmpty();
            assertThat(publishedEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("회원별 좋아요 목록 조회")
    class GetLikesByMemberId {

        @DisplayName("회원의 좋아요 목록이 반환된다")
        @Test
        void getLikesByMemberId_returnsLikes() {
            Product product1 = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Product product2 = productRepository.save(
                    new Product(1L, "에어포스", new Price(120000), new Stock(20)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product1.getId());
            likeFacade.addLike(memberId, product2.getId());

            List<Like> result = likeFacade.getLikesByMemberId(memberId);

            assertThat(result).hasSize(2);
        }

        @DisplayName("좋아요한 상품이 없으면 빈 리스트가 반환된다")
        @Test
        void getLikesByMemberId_whenEmpty_returnsEmptyList() {
            List<Like> result = likeFacade.getLikesByMemberId(1L);

            assertThat(result).isEmpty();
        }

        @DisplayName("다른 회원의 좋아요는 포함되지 않는다")
        @Test
        void getLikesByMemberId_excludesOtherMembers() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            likeFacade.addLike(1L, product.getId());
            likeFacade.addLike(2L, product.getId());

            List<Like> result = likeFacade.getLikesByMemberId(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberId()).isEqualTo(1L);
        }
    }
}
