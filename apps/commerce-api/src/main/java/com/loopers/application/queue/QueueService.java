package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueToken;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class QueueService {

    /**
     * 초당 처리량 (TPS).
     * Little's Law 역산: DB 커넥션 풀 200 * (1 / 평균 처리 시간 0.2s) * 안전 마진 70% ≈ 70
     * → 추후 Knee-of-the-Curve 부하 테스트로 실측 후 조정.
     * Feature Flag로 외부화 예정 (런타임 변경 가능하도록).
     */
    private static final long THROUGHPUT_PER_SECOND = 70L;

    /**
     * 입장 허가 임계값.
     * rank ≤ ADMIT_THRESHOLD 이면 입장 허가.
     * 폴링 1회당 ADMIT_THRESHOLD명을 동시 허가하는 구조.
     * Thundering Herd 완화를 위해 작게 유지 (Jitter는 Controller에서 처리).
     */
    private static final long ADMIT_THRESHOLD = 0L; // rank 0 (1등)만 즉시 허가. 배치 처리는 추후 확장.

    private final QueueRepository queueRepository;

    /**
     * 대기열 진입.
     * 상류 stateless 설계: 서버가 연결 상태를 유지하지 않음.
     * → Pod 자유 재시작/확장 가능 (블랙프라이데이 대응).
     */
    public QueueInfo.EnterInfo enter(Long userId, String queueId) {
        QueueToken token = QueueToken.create(userId, queueId);
        queueRepository.enter(token);

        long rank = queueRepository.getRank(queueId, userId).orElse(0L);
        long totalSize = queueRepository.getTotalSize(queueId);

        return new QueueInfo.EnterInfo(token.token(), rank, totalSize);
    }

    /**
     * 폴링 — 순위 조회 + 입장 허가 확인.
     *
     * pull 방식: 스케줄러 없이 유저 폴링이 임계점 도달 시 Lua로 자진 입장.
     * push(스케줄러) vs pull(폴링 감지) 트레이드오프:
     * - push: 정확히 N명 제어 용이, 별도 스케줄러 필요
     * - pull: 컴포넌트 단순, 동시 임계점 도달 시 N명 초과 가능성 → Lua 원자성으로 방지
     * 현재는 pull 방식으로 구현. 부하 테스트 후 push로 전환 여부 결정.
     */
    public QueueInfo.StatusInfo getStatus(String token, String queueId) {
        // 토큰으로 userId 추출 — 토큰 만료 시 재진입 유도
        Long userId = queueRepository.getUserIdByToken(token)
            .orElseThrow(() -> new CoreException(ErrorType.QUEUE_TOKEN_NOT_FOUND));

        long rank = queueRepository.getRank(queueId, userId).orElse(0L);
        long totalSize = queueRepository.getTotalSize(queueId);

        // rank ≤ ADMIT_THRESHOLD이면 Lua로 원자적 입장 허가 시도
        boolean admitted = false;
        if (rank <= ADMIT_THRESHOLD) {
            admitted = queueRepository.admit(queueId, userId, ADMIT_THRESHOLD);
        }

        // 예상 대기 시간 = 현재 순위 / 초당 처리량
        long etaSeconds = THROUGHPUT_PER_SECOND > 0 ? rank / THROUGHPUT_PER_SECOND : 0L;

        return new QueueInfo.StatusInfo(rank, totalSize, etaSeconds, admitted);
    }

    /**
     * 입장 허가 검증. Service API의 게이트키퍼 (Back-pressure Gate 1).
     * entered:{userId} 없으면 403 — 대기열 우회 차단.
     */
    public void validateEntry(Long userId) {
        if (!queueRepository.isEntered(userId)) {
            throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
        }
    }
}
