package com.loopers.domain.queue;

import com.loopers.support.enums.QueueStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 대기열 도메인 서비스.
 *
 * <p>대기열 진입, 순번 조회, 배치 추출을 담당한다.
 * Redis 구현은 {@link QueueRepository}에 위임하고, 비즈니스 규칙(maxSize, 대기 시간 계산)은
 * {@link QueueProperties}에 캡슐화되어 있다.</p>
 *
 * <p>기존 프로젝트의 서비스 패턴({@code UserService}, {@code OrderService})과 동일:
 * {@code @Service} + {@code @RequiredArgsConstructor} + 도메인 Repository 주입.</p>
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;
    private final EntryTokenService entryTokenService;

    /**
     * 대기열에 진입한다.
     *
     * <p>처리 흐름:
     * 1) maxSize 초과 여부 체크 → 초과 시 QUEUE_FULL
     * 2) ZADD NX로 대기열 추가 (이미 있으면 기존 순번 유지)
     * 3) ZRANK로 현재 순번 조회
     * 4) 예상 대기 시간 계산</p>
     *
     * <p>멱등성: 같은 userId로 재요청해도 기존 순번 반환 (ZADD NX).</p>
     *
     * @param userId 사용자 ID
     * @return 진입 결과 (순번 정보 + 신규 여부)
     * @throws CoreException QUEUE_FULL — 대기열이 가득 찬 경우
     */
    public EnterResult enter(Long userId) {
        // 1. 대기열 수용 가능 여부 체크
        long currentSize = queueRepository.getSize();
        if (!queueProperties.canAccept(currentSize)) {
            throw new CoreException(ErrorType.QUEUE_FULL);
        }

        // 2. ZADD NX — 이미 존재하면 false (멱등)
        double score = System.currentTimeMillis();
        boolean isNew = queueRepository.addIfAbsent(userId, score);

        // 3. ZRANK — 현재 순번 조회
        Long rank = queueRepository.getRank(userId);

        if (rank == null) {
            // ZADD 직후인데 ZRANK null → 스케줄러가 즉시 ZPOPMIN으로 꺼낸 경우
            // 토큰이 발급되었을 수 있음 → getPosition()으로 위임
            return new EnterResult(getPosition(userId), isNew);
        }

        int estimatedSeconds = queueProperties.calculateEstimatedWaitSeconds(rank);
        QueuePosition position = QueuePosition.waiting(rank, currentSize + (isNew ? 1 : 0), estimatedSeconds);
        return new EnterResult(position, isNew);
    }

    /**
     * 현재 대기 순번을 조회한다.
     *
     * <p>ZRANK로 대기열 존재 여부를 확인하고, 상태에 따라 WAITING 또는 NOT_IN_QUEUE를 반환.
     * Step 2에서 EntryTokenService 추가 후 READY 상태도 처리한다.</p>
     *
     * @param userId 사용자 ID
     * @return 순번 조회 결과
     */
    private static final String TOKEN_KEY_PREFIX = "order:entry-token:";

    /**
     * 현재 대기 순번을 조회한다.
     *
     * <p>Lua script로 ZRANK + ZCARD + GET(토큰)을 1 RTT에 원자적 조회.
     * Replica 우선 읽기로 Master 부하를 분산한다.</p>
     *
     * @param userId 사용자 ID
     * @return 순번 조회 결과
     */
    public QueuePosition getPosition(Long userId) {
        QueueRepository.PositionSnapshot snapshot =
                queueRepository.getPositionSnapshot(userId, TOKEN_KEY_PREFIX + userId);

        QueueStatus status = QueueStatus.evaluate(snapshot.rank() != null, snapshot.token() != null);

        return switch (status) {
            case WAITING -> {
                int estimated = queueProperties.calculateEstimatedWaitSeconds(snapshot.rank());
                yield QueuePosition.waiting(snapshot.rank(), snapshot.size(), estimated);
            }
            case READY -> QueuePosition.ready(snapshot.size(), snapshot.token());
            case NOT_IN_QUEUE -> {
                // Replica 지연 대응: Master에서 토큰 재확인
                // ZPOPMIN(Master) 직후 토큰 SET(Master)이 Replica에 미반영된 경우 방어
                String masterToken = queueRepository.getTokenFromMaster(userId);
                if (masterToken != null) {
                    yield QueuePosition.ready(snapshot.size(), masterToken);
                }
                yield QueuePosition.notInQueue(snapshot.size());
            }
        };
    }

    /**
     * 스케줄러가 호출 — 대기열 앞에서 N명을 원자적으로 꺼낸다.
     *
     * @param count 꺼낼 인원 수
     * @return 꺼낸 항목 리스트
     */
    public List<QueueEntry> popBatch(int count) {
        return queueRepository.popMin(count);
    }
}
