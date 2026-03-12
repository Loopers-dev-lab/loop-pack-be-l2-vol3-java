package com.loopers.application.cache;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderCacheIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final Long USER_ID = 1L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("주문 목록 캐시")
    class OrderListCache {

        @Test
        void 기본_조회_시_캐시에_저장된다() {
            // arrange
            createOrderDirectly(USER_ID, "ORD-TEST-001");

            // act — 기본 조회 (Controller 기본값 시뮬레이션)
            ZonedDateTime defaultStart = ZonedDateTime.now().minusMonths(3);
            ZonedDateTime defaultEnd = ZonedDateTime.now();
            orderFacade.getOrdersWithCursor(USER_ID, defaultStart, defaultEnd, null, null, 20, true);

            // assert
            String key = "orders:list:" + USER_ID;
            String cached = redisTemplate.opsForValue().get(key);
            assertThat(cached).isNotNull();
            assertThat(cached).contains("ORD-TEST-001");
        }

        @Test
        void 캐시_히트_시_동일한_결과를_반환한다() {
            // arrange
            createOrderDirectly(USER_ID, "ORD-TEST-001");
            ZonedDateTime defaultStart = ZonedDateTime.now().minusMonths(3);
            ZonedDateTime defaultEnd = ZonedDateTime.now();

            // act
            var firstResult = orderFacade.getOrdersWithCursor(USER_ID, defaultStart, defaultEnd, null, null, 20, true);
            var secondResult = orderFacade.getOrdersWithCursor(USER_ID, defaultStart, defaultEnd, null, null, 20, true);

            // assert
            assertThat(secondResult.orders()).hasSize(firstResult.orders().size());
            assertThat(secondResult.orders().get(0).orderNumber())
                    .isEqualTo(firstResult.orders().get(0).orderNumber());
        }

        @Test
        void 커스텀_기간_조회는_캐시를_사용하지_않는다() {
            // arrange
            createOrderDirectly(USER_ID, "ORD-TEST-001");

            // act — 커스텀 기간 (isDefaultQuery = false)
            ZonedDateTime now = ZonedDateTime.now();
            orderFacade.getOrdersWithCursor(USER_ID, now.minusDays(7), now, null, null, 20, false);

            // assert — 캐시에 저장되지 않음
            String key = "orders:list:" + USER_ID;
            assertThat(redisTemplate.opsForValue().get(key)).isNull();
        }

        @Test
        void 주문_취소_시_캐시가_삭제된다() {
            // arrange
            Order order = createOrderDirectly(USER_ID, "ORD-TEST-001");
            ZonedDateTime defaultStart = ZonedDateTime.now().minusMonths(3);
            ZonedDateTime defaultEnd = ZonedDateTime.now();

            orderFacade.getOrdersWithCursor(USER_ID, defaultStart, defaultEnd, null, null, 20, true);
            assertThat(redisTemplate.opsForValue().get("orders:list:" + USER_ID)).isNotNull();

            // act — 주문 취소 (afterCommit DELETE)
            orderFacade.cancelOrder(order.getId(), USER_ID);

            // assert
            assertThat(redisTemplate.opsForValue().get("orders:list:" + USER_ID)).isNull();
        }

        @Test
        void 유저별로_별도_캐시가_생성된다() {
            // arrange
            Long userId2 = 2L;
            createOrderDirectly(USER_ID, "ORD-USER1");
            createOrderDirectly(userId2, "ORD-USER2");
            ZonedDateTime defaultStart = ZonedDateTime.now().minusMonths(3);
            ZonedDateTime defaultEnd = ZonedDateTime.now();

            // act
            orderFacade.getOrdersWithCursor(USER_ID, defaultStart, defaultEnd, null, null, 20, true);
            orderFacade.getOrdersWithCursor(userId2, defaultStart, defaultEnd, null, null, 20, true);

            // assert
            assertThat(redisTemplate.opsForValue().get("orders:list:" + USER_ID)).contains("ORD-USER1");
            assertThat(redisTemplate.opsForValue().get("orders:list:" + userId2)).contains("ORD-USER2");
        }
    }

    private Order createOrderDirectly(Long userId, String orderNumber) {
        Brand brand = brandRepository.save(Brand.register("테스트브랜드", "설명"));
        Product product = productRepository.save(Product.register(brand.getId(), "테스트상품", "설명", 10000));

        List<OrderItem> items = List.of(
                OrderItem.snapshot(product.getId(), "테스트상품", "테스트브랜드", 10000, 1)
        );

        return orderService.create(userId, orderNumber, items,
                "테스터", "010-1234-5678",
                "수령인", "010-8765-4321",
                "12345", "서울시 강남구", "101호");
    }
}
