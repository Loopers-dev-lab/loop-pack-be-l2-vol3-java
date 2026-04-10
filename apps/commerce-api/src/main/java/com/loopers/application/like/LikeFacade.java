package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeAddedEvent;
import com.loopers.domain.like.LikeRemovedEvent;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.product.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final UserService userService;
    private final BrandService brandService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(String loginId, String rawPassword) {
        User user = userService.authenticate(loginId, rawPassword);
        List<Long> productIds = likeService.getLikedProductIds(user.getId());
        // 상품 목록을 IN 쿼리로 한 번에 조회 (N+1 방지)
        List<Product> products = productService.getProductsByIds(productIds);
        // 브랜드도 IN 쿼리로 한 번에 조회 후 Map으로 변환
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));
        return products.stream()
            .map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId())))
            .toList();
    }

    @Transactional
    public void addLike(String loginId, String rawPassword, Long productId) {
        User user = userService.authenticate(loginId, rawPassword);
        productService.getProduct(productId);
        likeService.addLike(user.getId(), productId);
        outboxEventPublisher.publish(
            KafkaTopics.CATALOG_EVENTS,
            productId.toString(),
            "LIKE_ADDED",
            Map.of("productId", productId)
        );
        eventPublisher.publishEvent(new LikeAddedEvent(user.getId(), productId));
    }

    @Transactional
    public void removeLike(String loginId, String rawPassword, Long productId) {
        User user = userService.authenticate(loginId, rawPassword);
        productService.getProduct(productId);
        likeService.removeLike(user.getId(), productId);
        outboxEventPublisher.publish(
            KafkaTopics.CATALOG_EVENTS,
            productId.toString(),
            "LIKE_REMOVED",
            Map.of("productId", productId)
        );
        eventPublisher.publishEvent(new LikeRemovedEvent(user.getId(), productId));
    }
}
