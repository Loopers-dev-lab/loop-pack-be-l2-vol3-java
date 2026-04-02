package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 대기열의 핵심 비즈니스 로직을 담당하는 도메인 서비스.
// 대기열 진입, 순번 조회, 활성화 여부 확인 등의 기능을 제공한다.
// 모든 대기열 조작은 피처 플래그(QUEUE_ENABLED)가 활성화된 상태에서만 동작한다.
@Slf4j
@RequiredArgsConstructor
@Service
public class QueueService {

    // 피처 플래그 조회 캐시 TTL (밀리초).
    // 스케줄러(100ms)와 Interceptor에서 빈번하게 호출되므로,
    // DB 부하를 줄이기 위해 5초간 캐시한다.
    // 플래그 변경 시 최대 5초의 반영 지연이 발생할 수 있으나,
    // 대기열 ON/OFF는 즉각적인 정밀도가 필요하지 않으므로 허용 가능하다.
    private static final long CACHE_TTL_MS = 5_000;

    private final QueueRepository queueRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final QueueTokenService queueTokenService;

    // 캐시 필드. volatile로 스레드 간 가시성을 보장한다.
    private volatile boolean cachedQueueEnabled = false;
    private volatile long lastCheckedTime = 0;

    // 유저를 대기열에 등록한다.
    // score로 현재 시각(ms)을 사용하여 선착순 정렬을 보장한다.
    // Redis Sorted Set의 NX 옵션으로 이미 대기 중인 유저의 중복 진입을 방지하며,
    // 이 경우에도 현재 순번을 정상적으로 반환한다.
    public long enter(Long userId) {
        validateQueueEnabled();

        double score = System.currentTimeMillis();
        queueRepository.enter(userId, score);

        long rank = queueRepository.getRank(userId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 순번 조회에 실패했습니다."));
        return rank + 1; // 0-based rank → 1-based position
    }

    // 유저의 현재 대기 상태를 조회한다.
    // 1) 이미 토큰이 발급된 유저 → QueuePositionInfo.ready(token) 반환 (즉시 입장 가능)
    // 2) 아직 대기 중인 유저 → QueuePositionInfo.waiting(rank, ...) 반환 (순번, 예상 대기시간, 폴링 주기 포함)
    // 토큰을 먼저 확인하여 스케줄러가 토큰 발급 후 대기열에서 제거하기 전의 race condition을 방어한다.
    public QueuePositionInfo getPosition(Long userId) {
        validateQueueEnabled();

        Optional<String> token = queueTokenService.findToken(userId);
        if (token.isPresent()) {
            return QueuePositionInfo.ready(token.get());
        }

        long rank = queueRepository.getRank(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 유저입니다."));

        return QueuePositionInfo.waiting(rank, QueueConstants.BATCH_SIZE, QueueConstants.INTERVAL_MS);
    }

    // DB의 feature_flag 테이블에서 대기열 활성화 여부를 확인한다.
    // 플래그가 존재하지 않으면 비활성(false)으로 간주한다.
    // 5초 TTL의 인메모리 캐시를 적용하여 스케줄러와 Interceptor의 빈번한 호출에 의한
    // DB 부하를 방지한다.
    public boolean isQueueEnabled() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedTime < CACHE_TTL_MS) {
            return cachedQueueEnabled;
        }

        boolean enabled = featureFlagRepository.findByFeatureKey(QueueConstants.QUEUE_FEATURE_KEY)
                .map(FeatureFlag::isEnabled)
                .orElse(false);

        cachedQueueEnabled = enabled;
        lastCheckedTime = now;
        return enabled;
    }

    public long getTotalCount() {
        return queueRepository.getTotalCount();
    }

    // 피처 플래그 캐시를 초기화한다.
    // 테스트 환경에서 Spring Context를 공유할 때 테스트 간 캐시 오염을 방지하기 위해 사용된다.
    public void resetCache() {
        lastCheckedTime = 0;
    }

    // 대기열 비활성 상태에서의 진입/조회 요청을 차단한다.
    private void validateQueueEnabled() {
        if (!isQueueEnabled()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기열이 비활성 상태입니다.");
        }
    }
}

