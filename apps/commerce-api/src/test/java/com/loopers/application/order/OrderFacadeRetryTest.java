package com.loopers.application.order;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = OrderFacadeRetryTest.TestConfig.class)
class OrderFacadeRetryTest {

    @Configuration
    @EnableRetry
    static class TestConfig {
        @Bean
        OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        CouponRepository couponRepository() {
            return mock(CouponRepository.class);
        }

        @Bean
        CouponIssueRepository couponIssueRepository() {
            return mock(CouponIssueRepository.class);
        }

        @Bean
        OrderService orderService() {
            return new OrderService();
        }

        @Bean
        OrderFacade orderFacade(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CouponRepository couponRepository,
            CouponIssueRepository couponIssueRepository,
            OrderService orderService,
            OrderPlacementTxService orderPlacementTxService
        ) {
            return new OrderFacade(
                orderRepository,
                productRepository,
                userRepository,
                couponRepository,
                couponIssueRepository,
                orderService,
                orderPlacementTxService
            );
        }

        @Bean
        OrderPlacementTxService orderPlacementTxService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            CouponRepository couponRepository,
            CouponIssueRepository couponIssueRepository,
            OrderService orderService
        ) {
            return new OrderPlacementTxService(
                orderRepository,
                productRepository,
                couponRepository,
                couponIssueRepository,
                orderService
            );
        }
    }

    @jakarta.annotation.Resource
    private OrderFacade orderFacade;

    @jakarta.annotation.Resource
    private OrderRepository orderRepository;

    @jakarta.annotation.Resource
    private ProductRepository productRepository;

    @jakarta.annotation.Resource
    private UserRepository userRepository;

    @jakarta.annotation.Resource
    private CouponRepository couponRepository;

    @jakarta.annotation.Resource
    private CouponIssueRepository couponIssueRepository;

    @BeforeEach
    void setUp() {
        reset(orderRepository, productRepository, userRepository, couponRepository, couponIssueRepository);
    }

    @DisplayName("@Retryable: 낙관적 락 충돌이 발생하면 placeOrder를 재시도한다")
    @Test
    void placeOrder_retries_whenOptimisticLockHappens() {
        Long userId = 1L;
        Long productId = 10L;

        User user = mock(User.class);
        Product product = mock(Product.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("재시도 상품");
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(1000));
        when(productRepository.save(product)).thenReturn(product);

        when(orderRepository.save(any(Order.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OrderDto.OrderInfo result = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            null
        );

        assertThat(result.items()).hasSize(1);
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @DisplayName("@Retryable: 쿠폰 낙관적 락 충돌 후 재시도 시 이미 사용된 쿠폰이면 COUPON_NOT_AVAILABLE 예외가 발생한다")
    @Test
    void placeOrder_throwsCouponNotAvailable_whenCouponAlreadyUsedOnRetry() {
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 100L;
        Long couponId = 1L;

        User user = mock(User.class);
        Product product = mock(Product.class);
        Coupon coupon = mock(Coupon.class);

        // 1차 시도: AVAILABLE 상태의 쿠폰
        CouponIssue availableIssue = CouponIssue.create(couponId, userId, ZonedDateTime.now().plusDays(7));
        // 2차 시도(재시도): 다른 트랜잭션이 먼저 커밋해서 이미 USED 상태인 쿠폰
        CouponIssue usedIssue = CouponIssue.create(couponId, userId, ZonedDateTime.now().plusDays(7));
        usedIssue.use();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("상품");
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(1000));
        when(productRepository.save(product)).thenReturn(product);

        when(couponIssueRepository.findByIdAndUserId(couponIssueId, userId))
            .thenReturn(Optional.of(availableIssue))  // 1차: AVAILABLE
            .thenReturn(Optional.of(usedIssue));       // 재시도: USED (다른 스레드가 먼저 사용)
        when(couponIssueRepository.save(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(CouponIssue.class, couponIssueId));

        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(coupon.calculateDiscount(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            couponIssueId
        ))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_AVAILABLE));

        verify(couponIssueRepository, times(2)).findByIdAndUserId(couponIssueId, userId);
    }
}
