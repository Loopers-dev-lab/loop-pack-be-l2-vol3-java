package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 도메인 {@link LikeRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link LikeJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository jpaRepository;

    /**
     * 좋아요를 저장한다.
     *
     * @param like 저장할 좋아요 엔티티
     * @return 저장된 좋아요 엔티티
     */
    @Override
    public LikeModel save(LikeModel like) {
        return jpaRepository.save(like);
    }

    /**
     * 복합 PK로 좋아요를 조회한다.
     *
     * @param id 복합 PK (사용자 ID + 상품 ID)
     * @return 좋아요 (Optional)
     */
    @Override
    public Optional<LikeModel> findById(LikeId id) {
        return jpaRepository.findById(id);
    }

    /**
     * 좋아요를 삭제한다.
     *
     * @param like 삭제할 좋아요 엔티티
     */
    @Override
    public void delete(LikeModel like) {
        jpaRepository.delete(like);
    }

    /**
     * 사용자 ID로 해당 사용자의 좋아요 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 좋아요 목록
     */
    @Override
    public List<LikeModel> findAllByUserId(String userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    /**
     * 상품 ID에 대한 좋아요 수를 조회한다.
     *
     * @param productId 상품 ID
     * @return 해당 상품의 좋아요 수
     */
    @Override
    public long countByProductId(String productId) {
        return jpaRepository.countByProductId(productId);
    }

    @Override
    public Map<String, Long> countByProductIds(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jpaRepository.countByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }
}
