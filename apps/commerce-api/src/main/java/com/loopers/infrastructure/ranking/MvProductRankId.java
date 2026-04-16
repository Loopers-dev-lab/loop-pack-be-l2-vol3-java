package com.loopers.infrastructure.ranking;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * mv_product_rank_weekly / mv_product_rank_monthly 복합 PK 클래스.
 *
 * JPA @IdClass 사양 요구사항:
 *   - Serializable 구현
 *   - 인수 없는 기본 생성자 (JPA 리플렉션 인스턴스화)
 *   - equals / hashCode 재정의 (동등성 비교)
 *
 * (product_id, base_date) 조합이 PK 이므로 동일 상품의 날짜별 랭킹을 각각 저장할 수 있다.
 * 이를 통해 과거 날짜의 랭킹 조회(이력 조회)가 가능하다.
 */
public class MvProductRankId implements Serializable {

    private Long productId;
    private LocalDate baseDate;

    // JPA 스펙: 인수 없는 기본 생성자 필수
    protected MvProductRankId() {
    }

    public MvProductRankId(Long productId, LocalDate baseDate) {
        this.productId = productId;
        this.baseDate = baseDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MvProductRankId that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(baseDate, that.baseDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, baseDate);
    }
}
