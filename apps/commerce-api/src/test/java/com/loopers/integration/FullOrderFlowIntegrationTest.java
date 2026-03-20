package com.loopers.integration;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItemId;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.cart.CartItemJpaRepository;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("전체 주문 플로우 통합 테스트")
class FullOrderFlowIntegrationTest {

    @Autowired UserService userService;
    @Autowired BrandService brandService;
    @Autowired ProductFacade productFacade;
    @Autowired OrderFacade orderFacade;
    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired CartFacade cartFacade;
    @Autowired StockService stockService;
    @Autowired CartItemJpaRepository cartItemJpaRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        String loginId = "testuser01";
        String loginPw = "Test1234!@#";
        var user = userService.register(new UserRegisterCommand(loginId, loginPw, "홍길동", "19900101", "test@example.com", "서울"));
        userId = user.getUserId();
    }

    @Test
    @DisplayName("scenario1: 바로 주문 → 취소 → 재고 영향 없음 + 장바구니 복원")
    void directOrder_Cancel_ShouldNotAffectStockAndRestoreCart() {
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductInfo product = productFacade.createProduct(
                new ProductCreateCommand("테스트상품", brand.getBrandId(), BigDecimal.valueOf(10000), "설명", 10));

        // 바로 주문 (수량 2) — Optimistic Stock: hold 없음
        List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getProductId(), 2));
        OrderInfo order = orderFacade.createDirectOrder(userId, items, null);

        // 재고 확인: hold 없으므로 reserved=0 유지
        ProductStockModel stock = stockService.findByProductId(product.getProductId());
        assertThat(stock.getReserved()).isEqualTo(0);
        assertThat(stock.getAvailableQty()).isEqualTo(10);

        // 주문 취소
        orderFacade.cancelOrder(userId, order.getOrderId());

        // 재고 여전히 변화 없음
        stock = stockService.findByProductId(product.getProductId());
        assertThat(stock.getReserved()).isEqualTo(0);
        assertThat(stock.getAvailableQty()).isEqualTo(10);

        // 장바구니 복원 확인 (DIRECT 주문)
        Optional<CartItemModel> cartItem = cartItemJpaRepository.findById(
                new CartItemId(order.getUserId(), product.getProductId()));
        assertThat(cartItem).isPresent();
        assertThat(cartItem.get().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("scenario2: 장바구니 주문 → 취소 → 재고 영향 없음 + 장바구니 유지")
    void cartOrder_Cancel_ShouldNotAffectStockAndKeepCart() {
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductInfo product1 = productFacade.createProduct(
                new ProductCreateCommand("상품1", brand.getBrandId(), BigDecimal.valueOf(10000), "설명", 10));
        ProductInfo product2 = productFacade.createProduct(
                new ProductCreateCommand("상품2", brand.getBrandId(), BigDecimal.valueOf(20000), "설명", 10));

        // 장바구니에 담기
        cartService.addItem(userId, product1.getProductId(), 3);
        cartService.addItem(userId, product2.getProductId(), 2);

        // 장바구니 주문 — Optimistic Stock: hold 없음
        List<OrderItemCommand> items = List.of(
                new OrderItemCommand(product1.getProductId(), 3),
                new OrderItemCommand(product2.getProductId(), 2));
        OrderInfo order = orderFacade.createCartOrder(userId, items, null);

        // 재고 확인: hold 없으므로 가용 재고 유지
        assertThat(stockService.findByProductId(product1.getProductId()).getAvailableQty()).isEqualTo(10);
        assertThat(stockService.findByProductId(product2.getProductId()).getAvailableQty()).isEqualTo(10);

        // 주문 취소
        orderFacade.cancelOrder(userId, order.getOrderId());

        // 재고 여전히 변화 없음
        assertThat(stockService.findByProductId(product1.getProductId()).getAvailableQty()).isEqualTo(10);
        assertThat(stockService.findByProductId(product2.getProductId()).getAvailableQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("scenario5: 브랜드 삭제 → 상품 연쇄 삭제 → 장바구니 unavailable")
    void brandDelete_ShouldCascadeProductDeleteAndCartUnavailable() {
        BrandModel brand = brandService.createBrand("삭제브랜드", "설명", "서울");
        ProductInfo product1 = productFacade.createProduct(
                new ProductCreateCommand("상품1", brand.getBrandId(), BigDecimal.valueOf(10000), "설명", 10));
        ProductInfo product2 = productFacade.createProduct(
                new ProductCreateCommand("상품2", brand.getBrandId(), BigDecimal.valueOf(20000), "설명", 10));

        // 장바구니에 담기
        cartService.addItem(userId, product1.getProductId(), 1);

        // 브랜드 삭제 (연쇄 삭제)
        brandService.deleteBrand(brand.getBrandId());

        // 장바구니에서 unavailable 확인
        List<CartInfo> cart = cartFacade.getCartForAdmin(userId);
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("scenario6: Optimistic Stock — 주문 생성은 재고 hold 없이 가능하고 재고에 영향 없다")
    void optimisticStock_ShouldNotHoldStockOnOrderCreation() {
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductInfo product = productFacade.createProduct(
                new ProductCreateCommand("테스트상품", brand.getBrandId(), BigDecimal.valueOf(10000), "설명", 5));

        List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getProductId(), 1));

        // 여러 건 생성 가능 (PENDING limit 제거, 재고 hold 없음)
        OrderInfo order1 = orderFacade.createDirectOrder(userId, items, null);
        OrderInfo order2 = orderFacade.createDirectOrder(userId, items, null);
        OrderInfo order3 = orderFacade.createDirectOrder(userId, items, null);
        OrderInfo order4 = orderFacade.createDirectOrder(userId, items, null);

        assertThat(order4.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
