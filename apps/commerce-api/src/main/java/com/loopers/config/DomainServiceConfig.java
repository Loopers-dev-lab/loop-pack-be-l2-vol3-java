package com.loopers.config;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartRepository;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public UserService userService(UserRepository userRepository, PasswordEncryptor passwordEncryptor) {
        return new UserService(userRepository, passwordEncryptor);
    }

    @Bean
    public BrandService brandService(BrandRepository brandRepository) {
        return new BrandService(brandRepository);
    }

    @Bean
    public ProductService productService(ProductRepository productRepository) {
        return new ProductService(productRepository);
    }

    @Bean
    public LikeService likeService(LikeRepository likeRepository) {
        return new LikeService(likeRepository);
    }

    @Bean
    public CartService cartService(CartRepository cartRepository) {
        return new CartService(cartRepository);
    }

    @Bean
    public OrderService orderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        return new OrderService(orderRepository, orderItemRepository);
    }
}
