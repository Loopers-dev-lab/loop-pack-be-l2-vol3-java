package com.loopers.interfaces.apiadmin;

import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 운영 상태 조회 REST API.
 *
 * <p>Outbox 적체 현황 등 운영 모니터링에 필요한 정보를 실시간으로 제공한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1/ops")
@RequiredArgsConstructor
public class AdminOpsV1Controller {

    private final OutboxEventRepository outboxRepository;

    /**
     * Outbox 이벤트 상태를 조회한다.
     *
     * @return PENDING 존재 여부
     */
    @GetMapping("/outbox/status")
    public ResponseEntity<ApiResponse<AdminOpsV1Dto.OutboxStatusResponse>> getOutboxStatus() {
        boolean pendingExists = !outboxRepository.findPendingEvents(1).isEmpty();

        return ResponseEntity.ok(ApiResponse.success(
            AdminOpsV1Dto.OutboxStatusResponse.builder()
                .pendingExists(pendingExists)
                .build()
        ));
    }
}
