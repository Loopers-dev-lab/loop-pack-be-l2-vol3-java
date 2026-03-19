package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 성능 테스트용 대량 데이터 시더 (local 프로파일 전용)
 *
 * 총 14개 테이블 시딩:
 * users(5K) → brands(500) → products(200K) → product_likes(~10M) → inventories(200K)
 * → brand_likes(~50K) → coupon_templates(50) → orders(100K) → order_items(~150K)
 * → payments(100K) → issued_coupons(30K) → point_accounts(5K) → cart_items(~15K)
 * → user_addresses(~8K)
 *
 * @see 도메인별 최적화 대상 분석 및 데이터 분포 설계.md
 */
@Component
@Profile("local")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserSeeder userSeeder;
    private final BrandSeeder brandSeeder;
    private final ProductSeeder productSeeder;
    private final ProductLikeSeeder productLikeSeeder;
    private final InventorySeeder inventorySeeder;
    private final BrandLikeSeeder brandLikeSeeder;
    private final CouponTemplateSeeder couponTemplateSeeder;
    private final OrderSeeder orderSeeder;
    private final PaymentSeeder paymentSeeder;
    private final IssuedCouponSeeder issuedCouponSeeder;
    private final PointAccountSeeder pointAccountSeeder;
    private final CartItemSeeder cartItemSeeder;
    private final UserAddressSeeder userAddressSeeder;

    public DataSeeder(
        JdbcTemplate jdbcTemplate,
        UserSeeder userSeeder,
        BrandSeeder brandSeeder,
        ProductSeeder productSeeder,
        ProductLikeSeeder productLikeSeeder,
        InventorySeeder inventorySeeder,
        BrandLikeSeeder brandLikeSeeder,
        CouponTemplateSeeder couponTemplateSeeder,
        OrderSeeder orderSeeder,
        PaymentSeeder paymentSeeder,
        IssuedCouponSeeder issuedCouponSeeder,
        PointAccountSeeder pointAccountSeeder,
        CartItemSeeder cartItemSeeder,
        UserAddressSeeder userAddressSeeder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userSeeder = userSeeder;
        this.brandSeeder = brandSeeder;
        this.productSeeder = productSeeder;
        this.productLikeSeeder = productLikeSeeder;
        this.inventorySeeder = inventorySeeder;
        this.brandLikeSeeder = brandLikeSeeder;
        this.couponTemplateSeeder = couponTemplateSeeder;
        this.orderSeeder = orderSeeder;
        this.paymentSeeder = paymentSeeder;
        this.issuedCouponSeeder = issuedCouponSeeder;
        this.pointAccountSeeder = pointAccountSeeder;
        this.cartItemSeeder = cartItemSeeder;
        this.userAddressSeeder = userAddressSeeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isAlreadySeeded()) {
            log.info("[DataSeeder] 데이터가 이미 존재합니다. 시딩을 건너뜁니다.");
            return;
        }

        log.info("[DataSeeder] ===== 성능 테스트용 대량 데이터 시딩 시작 (14개 테이블) =====");
        long start = System.currentTimeMillis();

        // Phase 1: 기본 엔티티
        userSeeder.seed();
        brandSeeder.seed();

        // Phase 2: 상품 관련
        productSeeder.seed();
        productLikeSeeder.seed();
        inventorySeeder.seed();
        brandLikeSeeder.seed();

        // Phase 3: 쿠폰
        couponTemplateSeeder.seed();

        // Phase 4: 주문 + 결제
        orderSeeder.seed();
        paymentSeeder.seed();
        issuedCouponSeeder.seed();

        // Phase 5: 유저 부가 데이터
        pointAccountSeeder.seed();
        cartItemSeeder.seed();
        userAddressSeeder.seed();

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DataSeeder] ===== 시딩 완료 ({}ms, {}초) =====", elapsed, elapsed / 1000);
    }

    private boolean isAlreadySeeded() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        return count != null && count > 0;
    }
}
