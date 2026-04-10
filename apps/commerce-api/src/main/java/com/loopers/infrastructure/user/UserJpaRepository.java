package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByLoginId(String loginId);
    Optional<UserModel> findByLoginIdAndDeletedAtIsNull(String loginId);
    Optional<UserModel> findByIdAndDeletedAtIsNull(Long id);
    Page<UserModel> findAllByDeletedAtIsNull(Pageable pageable);
    List<UserModel> findAllByDeletedAtIsNull();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserModel u SET u.point = u.point - :amount"
            + " WHERE u.id = :id AND u.point >= :amount AND u.deletedAt IS NULL")
    int deductPoint(@Param("id") Long id, @Param("amount") long amount);
}
