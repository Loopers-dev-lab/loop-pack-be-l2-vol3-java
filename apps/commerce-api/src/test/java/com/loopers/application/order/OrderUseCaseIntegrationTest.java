package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.coupon.CouponAdminApplicationService;
import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class OrderUseCaseIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CouponAdminApplicationService couponAdminApplicationService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("OrderUseCase 통합: 주문 생성 시 재고 차감 반영")
    void createOrderAndDecreaseStock() {
        UUID categoryId = categoryRepository.save(new Category("주문카테고리")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품",
                5000,
                5,
                "desc",
                categoryId,
                brandId
        ));

        Order order = orderUseCase.create(new CreateOrderCommand(
                "orderusemember",
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 2)),
                null
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(order.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(3);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 주문 취소 시 쿠폰이 복구되어 재사용 가능")
    void cancelOrderRestoresCoupon() {
        String memberId = "ordercouponmember";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리2")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드2", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품2",
                5000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "주문취소복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                LocalDateTime.now().plusDays(3)
        ));

        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        Order firstOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        orderUseCase.cancel(new OrderAccessRequest(firstOrder.id(), memberId, false));

        Order secondOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(secondOrder.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(9);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 관리자 취소에서도 쿠폰 복구가 동작한다")
    void adminCancelAlsoRestoresCoupon() {
        String memberId = "ordercouponadmin";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리3")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드3", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품3",
                5000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "관리자취소복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                LocalDateTime.now().plusDays(3)
        ));

        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        Order firstOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        orderUseCase.cancel(new OrderAccessRequest(firstOrder.id(), null, true));

        Order secondOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(secondOrder.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(9);
    }
}
