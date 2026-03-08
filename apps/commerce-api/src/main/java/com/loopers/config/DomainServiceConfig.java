package com.loopers.config;

import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.cart.CartRepository;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.domain.stock.ProductStockRepository;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserDomainService;
import com.loopers.domain.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public UserDomainService userDomainService(UserRepository userRepository, PasswordEncryptor passwordEncryptor) {
        return new UserDomainService(userRepository, passwordEncryptor);
    }

    @Bean
    public BrandDomainService brandDomainService(BrandRepository brandRepository) {
        return new BrandDomainService(brandRepository);
    }

    @Bean
    public ProductDomainService productDomainService(ProductRepository productRepository) {
        return new ProductDomainService(productRepository);
    }

    @Bean
    public LikeDomainService likeDomainService(LikeRepository likeRepository) {
        return new LikeDomainService(likeRepository);
    }

    @Bean
    public CartDomainService cartDomainService(CartRepository cartRepository) {
        return new CartDomainService(cartRepository);
    }

    @Bean
    public OrderDomainService orderDomainService(OrderRepository orderRepository) {
        return new OrderDomainService(orderRepository);
    }

    @Bean
    public CouponDomainService couponDomainService(CouponRepository couponRepository) {
        return new CouponDomainService(couponRepository);
    }

    @Bean
    public CouponIssueDomainService couponIssueDomainService(CouponIssueRepository couponIssueRepository) {
        return new CouponIssueDomainService(couponIssueRepository);
    }

    @Bean
    public ProductStockDomainService productStockDomainService(ProductStockRepository productStockRepository) {
        return new ProductStockDomainService(productStockRepository);
    }
}
