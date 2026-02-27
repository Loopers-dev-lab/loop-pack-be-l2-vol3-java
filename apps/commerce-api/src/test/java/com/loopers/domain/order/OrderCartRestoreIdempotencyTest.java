package com.loopers.domain.order;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.cart.CartItemId;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.cart.CartItemJpaRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("장바구니 복원 멱등성 테스트")
class OrderCartRestoreIdempotencyTest {

    @Autowired UserService userService;
    @Autowired BrandAppService brandAppService;
    @Autowired ProductFacade productFacade;
    @Autowired OrderFacade orderFacade;
    @Autowired CartService cartService;
    @Autowired CartItemJpaRepository cartItemJpaRepository;

    private String userId;
    private String loginId;
    private String loginPw;
    private String productId;

    @BeforeEach
    void setUp() {
        loginId = "testuser01";
        loginPw = "Test1234!@#";
        var user = userService.register(new UserRegisterCommand(loginId, loginPw, "홍길동", "19900101", "test@example.com", "서울"));
        userId = user.getUserId();

        BrandInfo brand = brandAppService.createBrand("테스트브랜드", "설명", "서울");
        ProductInfo product = productFacade.createProduct(
                new ProductCreateCommand("테스트상품", brand.getBrandId(), BigDecimal.valueOf(10000), "설명", 100));
        productId = product.getProductId();
    }

    @Test
    @DisplayName("2회 취소 요청 시 장바구니 항목이 중복 생성되지 않는다")
    void cancelDirectOrder_Twice_ShouldNotDuplicateCartItems() {
        // DIRECT 주문 생성
        List<OrderItemCommand> items = List.of(new OrderItemCommand(productId, 2));
        OrderInfo order = orderFacade.createDirectOrder(loginId, loginPw, items);

        // 1회 취소 (복원 수행)
        orderFacade.cancelOrder(loginId, loginPw, order.getOrderId());

        Optional<CartItemModel> afterFirst = cartItemJpaRepository.findById(new CartItemId(userId, productId));
        assertThat(afterFirst).isPresent();
        int qtyAfterFirst = afterFirst.get().getQuantity();

        // 2회 취소 시도 (이미 취소됨 — 멱등)
        orderFacade.cancelOrder(loginId, loginPw, order.getOrderId());

        Optional<CartItemModel> afterSecond = cartItemJpaRepository.findById(new CartItemId(userId, productId));
        assertThat(afterSecond).isPresent();
        assertThat(afterSecond.get().getQuantity()).isEqualTo(qtyAfterFirst);
    }

    @Test
    @DisplayName("기존 장바구니에 동일 상품 있을 때 수량이 병합된다")
    void cancelDirectOrder_WhenCartItemAlreadyExists_ShouldMergeQuantity() {
        // 기존 장바구니에 qty=3
        cartService.addItem(userId, productId, 3);

        // DIRECT 주문 (qty=2)
        List<OrderItemCommand> items = List.of(new OrderItemCommand(productId, 2));
        OrderInfo order = orderFacade.createDirectOrder(loginId, loginPw, items);

        // 취소 → 복원 시 병합
        orderFacade.cancelOrder(loginId, loginPw, order.getOrderId());

        Optional<CartItemModel> cartItem = cartItemJpaRepository.findById(new CartItemId(userId, productId));
        assertThat(cartItem).isPresent();
        assertThat(cartItem.get().getQuantity()).isEqualTo(5); // 3 + 2
    }

    @Test
    @DisplayName("수동 취소 후 만료 시도 시 재복원이 방지된다")
    void expireDirectOrder_AfterManualCancel_ShouldNotRestoreAgain() {
        // DIRECT 주문
        List<OrderItemCommand> items = List.of(new OrderItemCommand(productId, 2));
        OrderInfo order = orderFacade.createDirectOrder(loginId, loginPw, items);

        // 수동 취소 (복원 완료)
        orderFacade.cancelOrder(loginId, loginPw, order.getOrderId());

        Optional<CartItemModel> afterCancel = cartItemJpaRepository.findById(new CartItemId(userId, productId));
        assertThat(afterCancel).isPresent();
        int qtyAfterCancel = afterCancel.get().getQuantity();

        // 만료 시도 (CAS 실패 — 이미 CANCELLED)
        orderFacade.expireOrder(order.getOrderId());

        // 장바구니 변화 없음
        Optional<CartItemModel> afterExpire = cartItemJpaRepository.findById(new CartItemId(userId, productId));
        assertThat(afterExpire).isPresent();
        assertThat(afterExpire.get().getQuantity()).isEqualTo(qtyAfterCancel);
    }
}
