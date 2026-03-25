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
     * 상품 존재 확인 → 좋아요 등록 → 좋아요 수 증가 이벤트 발행
     *
     * 좋아요 수 증가는 AFTER_COMMIT 이벤트로 분리:
     * - 카운트 증가 실패가 좋아요 저장을 롤백시키지 않음 (Eventual Consistency)
     * - 크로스 도메인 결합도 감소 (LikeFacade → ProductService 직접 호출 제거)
     */
    @Transactional
    public LikeInfo create(Long userId, Long productId) {
        // 상품 존재 확인 (없으면 NOT_FOUND 예외)
        Product product = productService.findById(productId);
        // 좋아요 등록 (중복이면 CONFLICT 예외)
        Like like = likeService.create(userId, productId);
        // 좋아요 수 증가 이벤트 발행 (AFTER_COMMIT에서 처리)
        eventPublisher.publish(new LikeCreatedEvent(productId, userId));
        // 유저 행동 로깅
        userActionEventPublisher.publish(new UserActionEvent(
                UserActionEvent.ActionType.LIKE_CREATE, userId, "PRODUCT", productId, null));
        String brandName = brandService.findById(product.getBrandId()).getName();
        return LikeInfo.of(like, product, brandName);
    }

    /**
     * 좋아요 취소 (US-L02)
     * 좋아요 취소 → 좋아요 수 감소 이벤트 발행
     */
    @Transactional
    public void delete(Long userId, Long productId) {
        // 좋아요 취소 (없으면 NOT_FOUND 예외)
        likeService.delete(userId, productId);
        // 좋아요 수 감소 이벤트 발행 (AFTER_COMMIT에서 처리)
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
