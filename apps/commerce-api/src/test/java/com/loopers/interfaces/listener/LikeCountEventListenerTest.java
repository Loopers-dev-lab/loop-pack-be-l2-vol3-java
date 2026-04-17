package com.loopers.interfaces.listener;

import com.loopers.domain.event.LikeCreatedEvent;
import com.loopers.domain.event.LikeRemovedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LikeCountEventListenerTest {

    private LikeCountEventListener listener;
    private FakeProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();

        // 동기 실행 Executor + 스텁 TransactionManager
        var txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        listener = new LikeCountEventListener(productRepository, Runnable::run, txManager);
    }

    @Nested
    @DisplayName("LikeCreatedEvent 처리")
    class HandleLikeCreated {

        @DisplayName("LikeCreatedEvent 수신 시 상품의 likeCount가 1 증가한다")
        @Test
        void incrementsLikeCount() {
            Product product = productRepository.save(
                new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            listener.handleLikeCreated(new LikeCreatedEvent(product.getId(), 1L));

            assertThat(productRepository.findById(product.getId()).get().getLikeCount()).isEqualTo(1);
        }

        @DisplayName("여러 번 수신하면 likeCount가 누적된다")
        @Test
        void accumulatesLikeCount() {
            Product product = productRepository.save(
                new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            listener.handleLikeCreated(new LikeCreatedEvent(product.getId(), 1L));
            listener.handleLikeCreated(new LikeCreatedEvent(product.getId(), 2L));
            listener.handleLikeCreated(new LikeCreatedEvent(product.getId(), 3L));

            assertThat(productRepository.findById(product.getId()).get().getLikeCount()).isEqualTo(3);
        }

        @DisplayName("존재하지 않는 상품이면 예외 없이 무시된다 (best-effort)")
        @Test
        void whenProductNotExists_doesNotThrow() {
            listener.handleLikeCreated(new LikeCreatedEvent(999L, 1L));
        }
    }

    @Nested
    @DisplayName("LikeRemovedEvent 처리")
    class HandleLikeRemoved {

        @DisplayName("LikeRemovedEvent 수신 시 상품의 likeCount가 1 감소한다")
        @Test
        void decrementsLikeCount() {
            Product product = productRepository.save(
                new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            productRepository.incrementLikeCount(product.getId());
            productRepository.incrementLikeCount(product.getId());

            listener.handleLikeRemoved(new LikeRemovedEvent(product.getId(), 1L));

            assertThat(productRepository.findById(product.getId()).get().getLikeCount()).isEqualTo(1);
        }

        @DisplayName("likeCount가 0이면 음수가 되지 않는다")
        @Test
        void doesNotGoBelowZero() {
            Product product = productRepository.save(
                new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            listener.handleLikeRemoved(new LikeRemovedEvent(product.getId(), 1L));

            assertThat(productRepository.findById(product.getId()).get().getLikeCount()).isEqualTo(0);
        }

        @DisplayName("존재하지 않는 상품이면 예외 없이 무시된다 (best-effort)")
        @Test
        void whenProductNotExists_doesNotThrow() {
            listener.handleLikeRemoved(new LikeRemovedEvent(999L, 1L));
        }
    }
}
