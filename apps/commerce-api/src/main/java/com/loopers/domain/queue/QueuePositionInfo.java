package com.loopers.domain.queue;

// 대기열 내 유저의 현재 상태를 나타내는 불변 DTO.
// 대기 중(waiting)과 입장 가능(ready) 두 가지 상태를 팩토리 메서드로 구분한다.
//
// - waiting 상태: position(순번), estimatedWaitSeconds(예상 대기 시간), pollIntervalSeconds(폴링 주기) 제공
// - ready 상태: token(입장 토큰) 제공, position=0으로 즉시 입장 가능함을 의미
public record QueuePositionInfo(
        long position,
        long estimatedWaitSeconds,
        String token,
        int pollIntervalSeconds
) {
    // 대기 중인 유저의 위치 정보를 생성한다.
    // rank: Redis ZRANK 결과(0-based) → 유저에게는 1-based position으로 변환
    // 예상 대기 시간 계산: (position / batchSize) * (intervalMs / 1000)
    //   예) 100번째 유저, batchSize=18, intervalMs=100ms → 약 0.56초
    // 폴링 주기는 대기 순번에 따라 차등 적용하여 서버 부하를 분산한다.
    //   1~100번: 1초, 101~1000번: 3초, 1001번 이후: 5초
    public static QueuePositionInfo waiting(long rank, int batchSize, long intervalMs) {
        long userPosition = rank + 1;
        double batchCycles = (double) userPosition / batchSize;
        long estimatedWaitSeconds = Math.round(batchCycles * intervalMs / 1000.0);
        int pollInterval = calculatePollInterval(userPosition);
        return new QueuePositionInfo(userPosition, estimatedWaitSeconds, null, pollInterval);
    }

    // 토큰이 발급되어 즉시 입장 가능한 상태를 생성한다.
    // 클라이언트는 token 값이 non-null이면 주문 API 호출이 가능하다.
    public static QueuePositionInfo ready(String token) {
        return new QueuePositionInfo(0, 0, token, 0);
    }

    // 대기 순번 구간별 폴링 주기를 결정한다.
    // 앞쪽 유저는 곧 입장하므로 짧은 주기, 뒤쪽 유저는 긴 주기로 폴링하여
    // 불필요한 API 호출을 줄인다.
    private static int calculatePollInterval(long position) {
        if (position <= 100) {
            return 1;
        } else if (position <= 1000) {
            return 3;
        } else {
            return 5;
        }
    }
}
