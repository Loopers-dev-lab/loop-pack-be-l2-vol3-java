package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
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

    /**
     * 좋아요 등록 (US-L01)
     * 상품 존재 확인 → 좋아요 등록 → 좋아요 수 증가
     */
    @Transactional
    public LikeInfo create(Long userId, Long productId) {
        // 상품 존재 확인 (없으면 NOT_FOUND 예외)
        productService.findById(productId);
        // 좋아요 등록 (중복이면 CONFLICT 예외)
        Like like = likeService.create(userId, productId);
        // 원자적 좋아요 수 증가 (DB 레벨 UPDATE - flushAutomatically로 Like INSERT 먼저 flush됨)
        // clearAutomatically로 L1 캐시가 초기화되므로 아래 findById는 최신 likeCount를 반환함
        productService.increaseLikeCount(productId);
        Product product = productService.findById(productId);
        String brandName = brandService.findById(product.getBrandId()).getName();
        return LikeInfo.of(like, product, brandName);
    }

    /**
     * 좋아요 취소 (US-L02)
     * 좋아요 취소 → 좋아요 수 감소
     */
    @Transactional
    public void delete(Long userId, Long productId) {
        // 좋아요 취소 (없으면 NOT_FOUND 예외)
        likeService.delete(userId, productId);
        // 좋아요 수 감소
        productService.decreaseLikeCount(productId);
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
