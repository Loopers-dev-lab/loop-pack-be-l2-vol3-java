package com.loopers.infrastructure.stats;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.stats.StatsProjection;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({StatsRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("StatsRepository QueryDSL 통합 테스트")
class StatsRepositoryImplTest {

    @Autowired
    StatsRepositoryImpl statsRepository;

    @Autowired
    OrderJpaRepository orderJpaRepository;

    @Autowired
    OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    ProductJpaRepository productJpaRepository;

    @Autowired
    ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    BrandJpaRepository brandJpaRepository;

    @Autowired
    LikeJpaRepository likeJpaRepository;

    @Autowired
    TestEntityManager entityManager;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandJpaRepository.save(BrandModel.create("테스트브랜드", "설명", "서울"));
    }

    @Test
    @DisplayName("주문 상태별 건수가 정확하게 집계된다")
    void getOverview_ShouldCountByOrderStatus() {
        // PENDING 3건
        orderJpaRepository.save(OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        orderJpaRepository.save(OrderModel.create(2L, OrderType.DIRECT, BigDecimal.valueOf(20000)));
        orderJpaRepository.save(OrderModel.create(3L, OrderType.CART, BigDecimal.valueOf(30000)));
        // CANCELLED 2건
        OrderModel cancelled1 = orderJpaRepository.save(
                OrderModel.create(4L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        OrderModel cancelled2 = orderJpaRepository.save(
                OrderModel.create(5L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        entityManager.flush();
        entityManager.clear();
        orderJpaRepository.findById(cancelled1.getOrderId()).ifPresent(o -> {
            o.cancel();
            orderJpaRepository.save(o);
        });
        orderJpaRepository.findById(cancelled2.getOrderId()).ifPresent(o -> {
            o.cancel();
            orderJpaRepository.save(o);
        });
        // EXPIRED 1건
        OrderModel expired = orderJpaRepository.save(
                OrderModel.create(6L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        entityManager.flush();
        entityManager.clear();
        orderJpaRepository.findById(expired.getOrderId()).ifPresent(o -> {
            o.expire();
            orderJpaRepository.save(o);
        });
        entityManager.flush();
        entityManager.clear();

        LocalDate today = LocalDate.now();
        StatsProjection.Overview overview = statsRepository.getOverview(today.minusDays(1), today.plusDays(1));

        assertThat(overview.getPendingCount()).isEqualTo(3);
        assertThat(overview.getCancelledCount()).isEqualTo(2);
        assertThat(overview.getExpiredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일별 주문 통계가 GROUP BY 날짜로 집계된다")
    void getDailyOrderStats_ShouldGroupByDate() {
        orderJpaRepository.save(OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        orderJpaRepository.save(OrderModel.create(2L, OrderType.DIRECT, BigDecimal.valueOf(20000)));
        entityManager.flush();
        entityManager.clear();

        LocalDate today = LocalDate.now();
        List<StatsProjection.DailyOrderStat> stats = statsRepository.getDailyOrderStats(today, today);

        assertThat(stats).isNotEmpty();
        assertThat(stats.get(0).getOrderCount()).isEqualTo(2);
        assertThat(stats.get(0).getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("좋아요 상위 상품이 JOIN + GROUP BY로 정렬된다")
    void getTopLikedProducts_ShouldJoinAndAggregate() {
        ProductModel product1 = productJpaRepository.save(
                ProductModel.create("상품A", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        ProductModel product2 = productJpaRepository.save(
                ProductModel.create("상품B", brand.getBrandId(), BigDecimal.valueOf(20000),
                        null, null, null, null, null, null, null));
        ProductModel product3 = productJpaRepository.save(
                ProductModel.create("상품C", brand.getBrandId(), BigDecimal.valueOf(30000),
                        null, null, null, null, null, null, null));

        // product3: 3 likes, product1: 2 likes, product2: 1 like
        likeJpaRepository.save(LikeModel.create(1L, product1.getProductId()));
        likeJpaRepository.save(LikeModel.create(2L, product1.getProductId()));
        likeJpaRepository.save(LikeModel.create(1L, product2.getProductId()));
        likeJpaRepository.save(LikeModel.create(1L, product3.getProductId()));
        likeJpaRepository.save(LikeModel.create(2L, product3.getProductId()));
        likeJpaRepository.save(LikeModel.create(3L, product3.getProductId()));
        entityManager.flush();
        entityManager.clear();

        List<StatsProjection.ProductStat> result = statsRepository.getTopLikedProducts(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductName()).isEqualTo("상품C");
        assertThat(result.get(0).getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("주문 상위 상품이 GROUP BY로 정렬된다")
    void getTopOrderedProducts_ShouldJoinAndAggregate() {
        OrderModel order = orderJpaRepository.save(
                OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(50000)));
        entityManager.flush();

        ProductModel product1 = productJpaRepository.save(
                ProductModel.create("상품A", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        ProductModel product2 = productJpaRepository.save(
                ProductModel.create("상품B", brand.getBrandId(), BigDecimal.valueOf(20000),
                        null, null, null, null, null, null, null));
        entityManager.flush();

        orderItemJpaRepository.save(OrderItemModel.create(
                order.getOrderId(), 1, 1L, product1.getProductId(), 3,
                "상품A", BigDecimal.valueOf(10000), String.valueOf(brand.getBrandId()), "테스트브랜드", null));
        orderItemJpaRepository.save(OrderItemModel.create(
                order.getOrderId(), 2, 1L, product2.getProductId(), 1,
                "상품B", BigDecimal.valueOf(20000), String.valueOf(brand.getBrandId()), "테스트브랜드", null));
        entityManager.flush();
        entityManager.clear();

        List<StatsProjection.ProductStat> result = statsRepository.getTopOrderedProducts(10);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("가용 재고가 threshold 이하인 상품이 반환된다")
    void getLowStockProducts_ShouldFilterBelowThreshold() {
        ProductModel product1 = productJpaRepository.save(
                ProductModel.create("여유상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        ProductModel product2 = productJpaRepository.save(
                ProductModel.create("적정상품", brand.getBrandId(), BigDecimal.valueOf(20000),
                        null, null, null, null, null, null, null));
        ProductModel product3 = productJpaRepository.save(
                ProductModel.create("부족상품", brand.getBrandId(), BigDecimal.valueOf(30000),
                        null, null, null, null, null, null, null));

        productStockJpaRepository.save(ProductStockModel.createWithReserved(product1.getProductId(), 100, 90));
        productStockJpaRepository.save(ProductStockModel.createWithReserved(product2.getProductId(), 50, 10));
        productStockJpaRepository.save(ProductStockModel.createWithReserved(product3.getProductId(), 20, 18));
        entityManager.flush();
        entityManager.clear();

        List<StatsProjection.LowStockProduct> result = statsRepository.getLowStockProducts(5);

        // product3: available=2, product1 available=10 (threshold 이하 아님), product2 available=40
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("부족상품");
        assertThat(result.get(0).getAvailableQty()).isEqualTo(2);
    }
}
