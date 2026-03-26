package com.loopers.infrastructure.event;

import com.loopers.domain.event.UserActivityLogModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityLogJpaRepository extends JpaRepository<UserActivityLogModel, Long> {
}
