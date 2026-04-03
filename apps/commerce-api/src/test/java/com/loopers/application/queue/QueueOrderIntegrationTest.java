package com.loopers.application.queue;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class QueueOrderIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisTemplateMaster.delete("entry-token:" + USER_ID);
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand("나이키"));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(
                new Product(brandId, "나이키 에어맥스", new Money(10000), new Stock(10)));
    }

    @Nested
    @DisplayName("주문 완료 후 토큰 처리")
    class TokenAfterOrder {

        @Test
        @DisplayName("주문 성공 후 입장 토큰이 삭제된다")
        void 주문_성공_후_토큰_삭제됨() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            entryTokenRepository.issueIfAbsent(USER_ID, "test-token", 300L);
            OrderCreateCommand command = new OrderCreateCommand(
                    List.of(new OrderCreateCommand.Item(product.getId(), 1)), null);

            // act
            orderFacade.create(USER_ID, command);

            // assert — @Async AFTER_COMMIT 이벤트 완료까지 대기
            await().atMost(1, TimeUnit.SECONDS)
                    .until(() -> !entryTokenRepository.existsByUserId(USER_ID));
        }

        @Test
        @DisplayName("주문 실패 시 입장 토큰이 유지된다")
        void 주문_실패_시_토큰_유지() {
            // arrange
            entryTokenRepository.issueIfAbsent(USER_ID, "test-token", 300L);
            OrderCreateCommand command = new OrderCreateCommand(
                    List.of(new OrderCreateCommand.Item(NOT_EXISTED_PRODUCT_ID, 1)), null);

            // act & assert
            assertThrows(CoreException.class, () -> orderFacade.create(USER_ID, command));
            assertThat(entryTokenRepository.existsByUserId(USER_ID)).isTrue();
        }
    }
}
