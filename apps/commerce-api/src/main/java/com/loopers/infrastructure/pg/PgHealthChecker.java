package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.simulator.SimulatorFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PG Health Check Probe.
 *
 * <p>CB Open 상태에서 PG 생존 여부를 경량 GET 요청으로 확인한다.
 * 실제 결제 요청(POST)은 돈이 걸린 작업이므로 테스트용으로 쓰면 안 된다.</p>
 *
 * <p>200이든 404든 "응답이 왔다" = PG가 살아있다는 증거.
 * 500 에러나 타임아웃이면 아직 장애.</p>
 *
 * @see <a href="05-payment-resilience.md §7.6">Health Check Probe</a>
 * @see <a href="06-resilience-review.md §15.4">Half-Open 전략</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgHealthChecker {

    private final SimulatorFeignClient simulatorClient;

    /**
     * PG Simulator 서버가 살아있는지 확인.
     * 존재하지 않는 orderId로 조회 → 200/404 응답이면 서버 정상.
     */
    public boolean isSimulatorHealthy() {
        try {
            simulatorClient.getPaymentByOrderId("HEALTH_CHECK");
            return true;  // 200 — 서버 정상
        } catch (FeignException.NotFound e) {
            return true;  // 404 — 서버 살아있음, 데이터만 없음
        } catch (Exception e) {
            log.debug("PG Simulator Health Check 실패: {}", e.getMessage());
            return false; // 타임아웃/500/연결 실패 — 서버 장애
        }
    }
}
