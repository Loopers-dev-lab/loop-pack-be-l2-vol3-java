package com.loopers.infrastructure.collector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, String> {

    /**
     * 보관 주기 정리: {@code handled_at}이 cutoff 이전인 행을 삭제한다. (인덱스 idx_event_handled_handled_at 활용)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM event_handled WHERE handled_at < :cutoff LIMIT :limit", nativeQuery = true)
    int deleteHandledBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
