package com.loopers.application.like;

import com.loopers.domain.model.common.DomainEventPublisher;
import com.loopers.domain.model.like.Like;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.LikeRepository;
import com.loopers.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LikeService implements LikeUseCase, UnlikeUseCase {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final DomainEventPublisher domainEventPublisher;

    public LikeService(LikeRepository likeRepository, ProductRepository productRepository,
                       DomainEventPublisher domainEventPublisher) {
        this.likeRepository = likeRepository;
        this.productRepository = productRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public void like(UserId userId, Long productId) {
        findProduct(productId);

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }

        Like like = Like.create(userId, productId);
        likeRepository.save(like);
        domainEventPublisher.publishEvents(like);
    }

    @Override
    public void unlike(UserId userId, Long productId) {
        findProduct(productId);

        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(like -> {
                    Like unliked = like.markUnliked();
                    domainEventPublisher.publishEvents(unliked);
                    likeRepository.deleteByUserIdAndProductId(userId, productId);
                });
    }

    private Product findProduct(Long productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }
}
