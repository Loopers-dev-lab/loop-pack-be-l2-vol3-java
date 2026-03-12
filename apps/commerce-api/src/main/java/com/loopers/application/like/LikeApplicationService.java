package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 Application Service.
 * 여러 BC 조합 및 트랜잭션 경계 담당.
 */
@Service
@RequiredArgsConstructor
public class LikeApplicationService {

    private final LikeDomainService likeDomainService;
    private final ProductRepository productRepository;

    /**
     * 좋아요 등록.
     * 상품 존재 여부 검증 후 도메인 서비스 호출.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 생성된 좋아요 결과
     */
    @Transactional
    public LikeResult like(Long userId, Long productId) {
        validateProductExists(productId);
        Like like = likeDomainService.like(userId, productId);
        return LikeResult.from(like);
    }

    /**
     * 좋아요 취소.
     * 멱등하게 동작 - 존재하지 않아도 예외 없이 처리.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeDomainService.unlike(userId, productId);
    }

    private void validateProductExists(Long productId) {
        productRepository.findByIdActive(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
