package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeCancelledEvent;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeEventPublisher;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.domain.useraction.UserActionEventPublisher;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
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
    private final BrandService brandService;
    private final LikeEventPublisher eventPublisher;
    private final UserActionEventPublisher userActionEventPublisher;

    /**
     * 좋아요 등록 (US-L01)
     * 상품 존재 확인 → 좋아요 등록 → 이벤트 발행
     *
     * 이벤트 발행 이후의 모든 부가 로직은 Listener에서 처리:
     * - BEFORE_COMMIT: Outbox 테이블에 기록 (Kafka 발행 보장)
     * - AFTER_COMMIT: 좋아요 수 증가 (Eventual Consistency)
     */
    @Transactional
    public LikeInfo create(Long userId, Long productId) {
        // 상품 존재 확인 (없으면 NOT_FOUND 예외)
        Product product = productService.findById(productId);
        // 좋아요 등록 (중복이면 CONFLICT 예외)
        Like like = likeService.create(userId, productId);
        // 이벤트 발행 (이후 처리는 Listener가 담당)
        eventPublisher.publish(new LikeCreatedEvent(productId, userId));
        // 유저 행동 로깅
        userActionEventPublisher.publish(new UserActionEvent(
                UserActionEvent.ActionType.LIKE_CREATE, userId, "PRODUCT", productId, null));
        String brandName = brandService.findById(product.getBrandId()).getName();
        return LikeInfo.of(like, product, brandName);
    }

    /**
     * 좋아요 취소 (US-L02)
     * 좋아요 취소 → 이벤트 발행
     */
    @Transactional
    public void delete(Long userId, Long productId) {
        // 좋아요 취소 (없으면 NOT_FOUND 예외)
        likeService.delete(userId, productId);
        // 이벤트 발행 (이후 처리는 Listener가 담당)
        eventPublisher.publish(new LikeCancelledEvent(productId, userId));
        // 유저 행동 로깅
        userActionEventPublisher.publish(new UserActionEvent(
                UserActionEvent.ActionType.LIKE_CANCEL, userId, "PRODUCT", productId, null));
    }

    /**
     * 좋아요한 상품 목록 조회 (US-L03)
     * 회원의 좋아요 목록 조회 → 상품 정보 일괄 조회 → 조합
     */
    @Transactional(readOnly = true)
    public List<LikeInfo> findAllByUserId(Long userId) {
        List<Like> likes = likeService.findAllByUserId(userId);
        // 없으면 빈 목록 반환
        if (likes.isEmpty()) {
            return List.of();
        }

        // 상품 ID들만 추출
        List<Long> productIds = likes.stream().map(Like::getProductId).toList();
        // 추출한 상품ID로 상품정보 조회
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        // 상품정보 Map에서 브랜드ID만 추출
        List<Long> brandIds = productMap.values().stream().map(Product::getBrandId).distinct().toList();
        // 브랜드ID로 브랜드명 조회
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        return likes.stream()
                // 삭제된 상품 등, 유효하지 않은 상품 필터링(방어 코드)
                .filter(like -> productMap.containsKey(like.getProductId()))
                .map(like -> {
                    Product product = productMap.get(like.getProductId());
                    String brandName = brandNameMap.get(product.getBrandId());
                    return LikeInfo.of(like, product, brandName);
                })
                .toList();
    }
}
