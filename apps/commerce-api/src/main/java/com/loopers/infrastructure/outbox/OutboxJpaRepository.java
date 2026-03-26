package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxModel, Long> {

    @Query("SELECT o FROM OutboxModel o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxModel> findByStatusOrderByCreatedAtAsc(@Param("status") OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query(value = "UPDATE outbox SET status = 'PUBLISHED', published_at = NOW(), updated_at = NOW() WHERE id IN (:ids)", nativeQuery = true)
    void markAllPublished(@Param("ids") List<Long> ids);
}
