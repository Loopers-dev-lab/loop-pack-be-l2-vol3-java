package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.support.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * CAS(Compare-And-Set) 기반의 상태 변경 쿼리와 다양한 조회 쿼리를 정의한다.</p>
 */
public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    /**
     * 주문 ID와 사용자 ID로 주문을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param orderId 주문 ID
     * @param userId  사용자 ID
     * @return 주문 (Optional)
     */
    Optional<OrderModel> findByOrderIdAndUserId(Long orderId, Long userId);

    /**
     * 사용자 ID와 기간으로 주문 목록을 조회한다 (최신순 정렬).
     *
     * @param userId 사용자 ID
     * @param start  조회 시작 일시
     * @param end    조회 종료 일시
     * @return 기간 내 해당 사용자의 주문 목록
     */
    @Query("SELECT o FROM OrderModel o " +
           "WHERE o.userId = :userId AND o.createdAt BETWEEN :start AND :end " +
           "ORDER BY o.createdAt DESC")
    List<OrderModel> findAllByUserIdAndPeriod(@Param("userId") Long userId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 기간으로 전체 주문 목록을 조회한다 (최신순 정렬).
     *
     * @param start 조회 시작 일시
     * @param end   조회 종료 일시
     * @return 기간 내 전체 주문 목록
     */
    @Query("SELECT o FROM OrderModel o " +
           "WHERE o.createdAt BETWEEN :start AND :end " +
           "ORDER BY o.createdAt DESC")
    List<OrderModel> findAllByPeriod(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * CAS(Compare-And-Set) 방식으로 주문 상태를 변경한다.
     *
     * <p>현재 상태가 {@code fromStatus}인 경우에만 {@code toStatus}로 변경하여
     * 동시성 경쟁 조건을 방지한다.</p>
     *
     * @param orderId    주문 ID
     * @param fromStatus 변경 전 상태 (조건)
     * @param toStatus   변경 후 상태
     * @return 변경된 행 수 (0이면 상태 전이 실패)
     */
    @Modifying
    @Query("UPDATE OrderModel o SET o.status = :toStatus, o.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE o.orderId = :orderId AND o.status = :fromStatus")
    int casUpdateStatus(@Param("orderId") Long orderId,
                        @Param("fromStatus") OrderStatus fromStatus,
                        @Param("toStatus") OrderStatus toStatus);

    /**
     * 사용자 ID와 주문 상태로 주문 건수를 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: COUNT 쿼리가 자동 생성된다.</p>
     *
     * @param userId 사용자 ID
     * @param status 주문 상태
     * @return 조건에 해당하는 주문 건수
     */
    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /**
     * 만료 시각이 지난 결제 대기 주문 목록을 조회한다.
     *
     * <p>배치에서 만료 처리할 주문을 찾기 위해 사용한다.
     * 조건: 상태=PENDING_PAYMENT, 미삭제(del_yn='N'), 만료 시각 경과</p>
     *
     * @return 만료 대상 결제 대기 주문 목록
     */
    @Query("SELECT o FROM OrderModel o " +
           "WHERE o.status = 'PENDING_PAYMENT' AND o.delYn = 'N' AND o.expiresAt < CURRENT_TIMESTAMP")
    List<OrderModel> findExpiredPendingOrders();
}
