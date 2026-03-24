package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.like.LikeEventPublisher;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;
    private final BrandRepository brandRepository;
    private final LikeAssembler likeAssembler;
    private final LikeEventPublisher likeEventPublisher;

    @Transactional
    public void like(Long userId, Long productId) {
        if (!userRepository.existsById(userId)) throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.");
        if (!productRepository.existsById(productId)) throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다.");

        boolean inserted = likeRepository.saveIfAbsent(Like.of(userId, productId));
        if (inserted) {
            likeEventPublisher.publish(new LikeEvent.Created(userId, productId));
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        int deleted = likeRepository.deleteByUserIdAndProductId(userId, productId);
        if (deleted > 0) {
            likeEventPublisher.publish(new LikeEvent.Deleted(userId, productId));
        }
    }

    @Transactional(readOnly = true)
    public List<LikeProductInfo> getLikeList(Long userId) {
        List<Like> likes = likeRepository.findAllByUserId(userId);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(Like::productId).toList();
        List<Product> products = productRepository.findAllByIdIn(productIds);

        List<Long> brandIds = products.stream().map(Product::brandId).distinct().toList();
        List<Brand> brands = brandRepository.findAllByIdIn(brandIds);

        return likeAssembler.toInfos(products, brands);
    }

}
