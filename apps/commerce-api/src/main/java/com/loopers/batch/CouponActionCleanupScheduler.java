package com.loopers.batch;

import com.loopers.domain.coupon.CouponPendingActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 비동기 액션 정리 스케줄러.
 * <p>
 * 매일 03:30에 DONE 상태이고 생성일이 3일 이전인 액션을 삭제한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponActionCleanupScheduler {

    private static final int RETENTION_DAYS = 3;

    private final CouponPendingActionRepository couponPendingActionRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = couponPendingActionRepository.deleteDoneOlderThan(threshold);
        if (deleted > 0) {
            log.info("쿠폰 액션 정리 완료: {}건 삭제 (기준: {}일 이전)", deleted, RETENTION_DAYS);
        }
    }
}
