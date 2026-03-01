package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = OrderApplicationServiceRetryTest.TestConfig.class)
class OrderApplicationServiceRetryTest {

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
        OrderDomainService orderDomainService() {
            return new OrderDomainService();
        }

        @Bean
        OrderApplicationService orderApplicationService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderDomainService orderDomainService
        ) {
            return new OrderApplicationService(orderRepository, productRepository, userRepository, orderDomainService);
        }
    }

    @jakarta.annotation.Resource
    private OrderApplicationService orderApplicationService;

    @jakarta.annotation.Resource
    private OrderRepository orderRepository;

    @jakarta.annotation.Resource
    private ProductRepository productRepository;

    @jakarta.annotation.Resource
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        reset(orderRepository, productRepository, userRepository);
    }

    @DisplayName("@Retryable: 낙관적 락 충돌이 발생하면 placeOrder를 재시도한다")
    @Test
    void placeOrder_retries_whenOptimisticLockHappens() {
        Long userId = 1L;
        Long productId = 10L;

        User user = mock(User.class);
        Product product = mock(Product.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("재시도 상품");
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(1000));
        when(productRepository.save(product)).thenReturn(product);

        when(orderRepository.save(any(Order.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OrderDto.OrderInfo result = orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1))
        );

        assertThat(result.items()).hasSize(1);
        verify(orderRepository, times(2)).save(any(Order.class));
    }
}
