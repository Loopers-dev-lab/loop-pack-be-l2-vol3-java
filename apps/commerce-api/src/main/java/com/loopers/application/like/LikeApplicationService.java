package com.loopers.application.like;

import com.loopers.application.like.event.LikeEvent;
import com.loopers.application.like.event.LikeEvent.LikeAction;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeApplicationService {

    private final LikeDomainService likeService;
    private final ProductDomainService productService;
    private final BrandDomainService brandService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Like 저장(핵심 로직) 후 likeCount 증감은 이벤트로 분리한다.
     * Like 엔티티가 source of truth이며, likeCount는 eventual consistency로 반영된다.
     */
    @Transactional
    public void like(Long userId, Long productId) {
        productService.getById(productId);
        likeService.like(userId, productId);
        eventPublisher.publishEvent(new LikeEvent(userId, productId, LikeAction.LIKED, ZonedDateTime.now()));
    }

    /**
     * Like 삭제(핵심 로직) 후 likeCount 감소는 이벤트로 분리한다.
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
        eventPublisher.publishEvent(new LikeEvent(userId, productId, LikeAction.UNLIKED, ZonedDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<Like> getMyLikes(Long userId) {
        return likeService.getMyLikes(userId);
    }

    @Transactional(readOnly = true)
    public List<LikedProductDetail> getMyLikesWithDetails(Long userId) {
        List<Like> likes = likeService.getMyLikes(userId);

        Set<Long> productIds = likes.stream()
            .map(Like::getProductId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        return likes.stream()
            .filter(like -> {
                Product product = productMap.get(like.getProductId());
                return product != null && brandMap.containsKey(product.getBrandId());
            })
            .map(like -> {
                Product product = productMap.get(like.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return new LikedProductDetail(like, product, brand);
            })
            .toList();
    }
}
