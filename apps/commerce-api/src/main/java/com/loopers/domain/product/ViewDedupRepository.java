package com.loopers.domain.product;

import java.time.LocalDate;

public interface ViewDedupRepository {

    /**
     * 유저의 오늘 조회 여부를 체크하고 마킹한다.
     * SETBIT 반환값을 활용하여 체크+마킹을 원자적으로 수행.
     *
     * @return true 최초 조회 (이벤트 발행 필요), false 중복 조회 (스킵)
     */
    boolean markIfFirstView(Long productDbId, Long memberId, LocalDate date);
}
