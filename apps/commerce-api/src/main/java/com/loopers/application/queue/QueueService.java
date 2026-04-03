package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueToken;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueueService {

    /**
     * 초당 처리량 (TPS).
     * Little's Law 역산: DB 커넥션 풀 40 * (1 / 평균 처리 시간 0.2s) * 안전 마진 70% ≈ 140
     * → 추후 Knee-of-the-Curve 부하 테스트로 실측 후 조정.
     * Feature Flag로 외부화 예정 (런타임 변경 가능하도록).
     */
    private static final long THROUGHPUT_PER_SECOND = 140L;

    private final QueueRepository queueRepository;
    private final QueueSseRegistry sseRegistry;

    /**
     * 대기열 진입.
     * 상류 stateless 설계: 서버가 연결 상태를 유지하지 않음.
     * → Pod 자유 재시작/확장 가능 (블랙프라이데이 대응).
     */
    public QueueInfo.EnterInfo enter(Long userId, String queueId) {
        try {
            QueueToken token = QueueToken.create(userId, queueId);
            queueRepository.enter(token);

            long rank = queueRepository.getRank(queueId, userId).orElse(0L);
            long totalSize = queueRepository.getTotalSize(queueId);

            return new QueueInfo.EnterInfo(token.token(), rank, totalSize);
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Queue] Redis 장애 — enter() 실패. userId={}", userId, e);
            throw new CoreException(ErrorType.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 폴링 — 순위 조회만 수행.
     *
     * 변경 이유: 스케줄러가 유일한 admitter.
     * pull(폴링) + push(스케줄러) 동시 존재 시 이중 입장 가능성 있음.
     * → getStatus()에서 admit() 제거, isEntered() 체크만.
     *
     * admitted=true: 스케줄러가 설정한 entered:{userId} 키 존재 여부.
     */
    public QueueInfo.StatusInfo getStatus(String token, String queueId) {
        try {
            Long userId = queueRepository.getUserIdByToken(token)
                .orElseThrow(() -> new CoreException(ErrorType.QUEUE_TOKEN_NOT_FOUND));

            long rank = queueRepository.getRank(queueId, userId).orElse(0L);
            long totalSize = queueRepository.getTotalSize(queueId);

            // 스케줄러가 설정한 entered 키 확인 (pull 방식 admit 제거)
            boolean admitted = queueRepository.isEntered(userId);

            long etaSeconds = THROUGHPUT_PER_SECOND > 0 ? rank / THROUGHPUT_PER_SECOND : 0L;

            return new QueueInfo.StatusInfo(rank, totalSize, etaSeconds, admitted);
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Queue] Redis 장애 — getStatus() 실패. token={}", token, e);
            throw new CoreException(ErrorType.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 토큰으로 userId 조회. SSE 구독 등록 시 사용.
     * 토큰 만료 시 CoreException(QUEUE_TOKEN_NOT_FOUND).
     */
    public Long getUserIdByToken(String token) {
        try {
            return queueRepository.getUserIdByToken(token)
                .orElseThrow(() -> new CoreException(ErrorType.QUEUE_TOKEN_NOT_FOUND));
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Queue] Redis 장애 — getUserIdByToken() 실패. token={}", token, e);
            throw new CoreException(ErrorType.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 배치 입장 허가 — 스케줄러 전용 진입점.
     *
     * 1초마다 THROUGHPUT_PER_SECOND명을 ZPOPMIN으로 원자적으로 꺼내 entered 상태로 전환.
     * 입장 허가된 userId마다 SSE push → 클라이언트에 즉시 알림.
     *
     * fixedDelay(스케줄러)와 조합: 이전 실행 완료 후 1초 대기 → 중첩 실행 없음.
     */
    public void processBatch(String queueId) {
        List<Long> admittedUserIds = queueRepository.admitBatch(queueId, THROUGHPUT_PER_SECOND);
        if (admittedUserIds.isEmpty()) return;

        log.info("[Queue] Batch admit 완료. queueId={}, admitted={}명", queueId, admittedUserIds.size());

        // 입장 허가된 유저에게 SSE push
        for (Long userId : admittedUserIds) {
            sseRegistry.notifyAdmitted(String.valueOf(userId));
        }
    }

    /**
     * 입장 허가 검증. Service API의 게이트키퍼 (Back-pressure Gate).
     * entered:{userId} 없으면 403 — 대기열 우회 차단.
     */
    public void validateEntry(Long userId) {
        try {
            if (!queueRepository.isEntered(userId)) {
                throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
            }
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Queue] Redis 장애 — validateEntry() 실패. userId={}", userId, e);
            throw new CoreException(ErrorType.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 주문 완료 후 entered 키 삭제.
     * 5분 TTL 만료 전 명시적 삭제 → 슬롯 즉시 반환 → 다음 유저 빠른 입장.
     */
    public void deleteEntered(Long userId) {
        try {
            queueRepository.deleteEntered(userId);
        } catch (Exception e) {
            // 삭제 실패는 TTL 만료로 자연 정리되므로 warn만 기록 (주문 롤백 불필요)
            log.warn("[Queue] entered 키 삭제 실패. userId={}. TTL 만료 후 자동 정리됩니다.", userId, e);
        }
    }
}
