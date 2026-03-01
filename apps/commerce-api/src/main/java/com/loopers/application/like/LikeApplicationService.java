package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final LikeDomainService likeDomainService;

    @Transactional
    @Retryable(
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class,
            DataIntegrityViolationException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 30)
    )
    public LikeDto.LikeInfo like(Long userId, Long productId) {
        return likeWithStatus(userId, productId).like();
    }

    @Transactional
    @Retryable(
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class,
            DataIntegrityViolationException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 30)
    )
    public LikeDto.LikeResult likeWithStatus(Long userId, Long productId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        Like existing = likeRepository.findByUserIdAndProductIdIncludingDeleted(userId, productId)
            .orElse(null);

        LikeDomainService.LikeProcessResult result = likeDomainService.like(userId, productId, existing);

        if (!result.requiresPersistence()) {
            return new LikeDto.LikeResult(LikeDto.LikeInfo.from(result.like()), true);
        }

        product.increaseLikeCount();
        Like saved = likeRepository.save(result.like());
        productRepository.save(product);
        return new LikeDto.LikeResult(LikeDto.LikeInfo.from(saved), false);
    }

    @Transactional
    @Retryable(
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 30)
    )
    public void unlike(Long userId, Long productId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        Like like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElse(null);

        if (!likeDomainService.unlike(like)) {
            return;
        }

        product.decreaseLikeCount();
        likeRepository.save(like);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<LikeDto.LikedProductInfo> getMyLikes(Long userId, Pageable pageable) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        Page<Like> likes = likeRepository.findByUserId(userId, pageable);
        if (likes.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<Long> productIds = likes.stream()
            .map(Like::getProductId)
            .collect(Collectors.toSet());

        Map<Long, Product> productsById = productRepository.findAllByIdsIncludingDeleted(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        Set<Long> brandIds = productsById.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());

        Map<Long, String> brandNameById = brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Brand::getName));

        return new PageImpl<>(
            likes.getContent().stream()
                .map(like -> {
                    Product product = productsById.get(like.getProductId());
                    if (product == null) {
                        return null;
                    }
                    return LikeDto.LikedProductInfo.of(
                        like,
                        product,
                        brandNameById.getOrDefault(product.getBrandId(), "")
                    );
                })
                .filter(info -> info != null)
                .toList(),
            pageable,
            likes.getTotalElements()
        );
    }
}
